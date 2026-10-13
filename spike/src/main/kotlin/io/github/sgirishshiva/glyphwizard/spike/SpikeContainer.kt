// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.app.Application
import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.TimeSource

class SpikeApplication : Application() {
    val container: SpikeContainer by lazy { SpikeContainer() }
}

/** The spike's composition root: everything it wires lives for the whole process. */
class SpikeContainer(
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    val profile: DeviceProfile = DeviceProfile.Phone4aPro

    // outlives any service, so a release scheduled in onDestroy still runs
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
