package com.danielealbano.androidremotecontrolmcp.e2e

import com.danielealbano.androidremotecontrolmcp.e2e.AndroidContainerSetup.runProcess
import java.io.File
import java.io.IOException

/**
 * Host-side kernel prerequisites for redroid: the binder (binderfs) and fuse devices the container needs, and the
 * device numbers [AndroidContainerSetup.createContainer] passes through to it.
 */
object RedroidHostPrerequisites {
    /**
     * Ensures binder and fuse are available to redroid.
     *
     * On CI, modules are pre-loaded and binderfs mounted by the workflow. When [kernelPrerequisitesSatisfied] reports
     * them usable (binderfs mounted, fuse loaded or built in) nothing is done; otherwise attempts `sudo modprobe`
     * plus the binderfs mount and provides a clear error on failure.
     *
     * NOTE: Uses `sudo` — accepted exception to CLAUDE.md no-sudo rule.
     * Kernel module loading is a host-OS operation required by redroid.
     */
    internal fun ensureKernelModules() {
        val satisfied =
            kernelPrerequisitesSatisfied(
                procModules = readProcModules(),
                devFuseExists = File("/dev/fuse").exists(),
                binderfsMounted = isBinderfsMounted(),
            )
        if (satisfied) {
            println("[E2E Setup] Kernel prerequisites already available (binder, fuse)")
            return
        }

        println("[E2E Setup] Loading kernel modules via sudo modprobe...")
        try {
            runProcess(
                "sudo",
                "modprobe",
                "binder_linux",
                "devices=binder,hwbinder,vndbinder",
                timeoutSeconds = 60L,
            )
            runProcess("sudo", "modprobe", "fuse", timeoutSeconds = 60L)
            ensureBinderfs()
            println("[E2E Setup] Kernel modules loaded successfully")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to load kernel modules required by redroid. " +
                    "On Linux, ensure 'binder_linux' and 'fuse' modules are available. " +
                    "Install with: sudo apt-get install linux-modules-extra-\$(uname -r) && " +
                    "sudo modprobe binder_linux devices=\"binder,hwbinder,vndbinder\" && " +
                    "sudo modprobe fuse. Error: ${e.message}",
                e,
            )
        }
    }

    /**
     * Mounts binderfs at /dev/binderfs/ if not already mounted.
     * NOTE: Uses `sudo` — same accepted exception as ensureKernelModules.
     */
    private fun ensureBinderfs() {
        if (isBinderfsMounted()) return

        println("[E2E Setup] Mounting binderfs at /dev/binderfs/...")
        runProcess("sudo", "mkdir", "-p", "/dev/binderfs")
        runProcess("sudo", "mount", "-t", "binder", "binder", "/dev/binderfs")
    }

    /**
     * True when binder and fuse are already usable, so the sudo `modprobe` path can be skipped. Binder counts only
     * when `/dev/binderfs` is mounted, module or built in: [detectBinderDevMajor] reads the device major from that
     * mount, so a loaded `binder_linux` without it still needs [ensureBinderfs]. Built-in (non-module) fuse never
     * appears in [procModules], so an existing `/dev/fuse` also counts.
     */
    internal fun kernelPrerequisitesSatisfied(
        procModules: String,
        devFuseExists: Boolean,
        binderfsMounted: Boolean,
    ): Boolean = binderfsMounted && (devFuseExists || procModules.contains("fuse"))

    private fun readProcModules(): String =
        try {
            File("/proc/modules").readText()
        } catch (_: IOException) {
            ""
        }

    private fun isBinderfsMounted(): Boolean =
        try {
            runProcess("mountpoint", "-q", "/dev/binderfs")
            true
        } catch (_: IllegalStateException) {
            // Non-zero exit (not a mount point) or timeout.
            false
        } catch (_: IOException) {
            // `mountpoint` could not be started.
            false
        }

    internal fun detectBinderDevMajor(): String =
        try {
            val output = runProcess("stat", "-c", "%Hr", "/dev/binderfs/binder-control")
            if (output.isNotEmpty()) {
                println("[E2E Setup] Binder device major: $output")
                output
            } else {
                println("[E2E Setup] Could not detect binder device major, using default 10")
                "10"
            }
        } catch (e: Exception) {
            println("[E2E Setup] Binder device major detection failed: ${e.message}, using default 10")
            "10"
        }

    internal fun detectFuseDevMajorMinor(): String =
        try {
            val content = File("/sys/class/misc/fuse/dev").readText().trim()
            if (content.isNotEmpty()) {
                println("[E2E Setup] Fuse device major:minor: $content")
                content
            } else {
                println("[E2E Setup] Could not detect fuse device, using default 10:229")
                "10:229"
            }
        } catch (e: Exception) {
            println("[E2E Setup] Fuse device detection failed: ${e.message}, using default 10:229")
            "10:229"
        }
}
