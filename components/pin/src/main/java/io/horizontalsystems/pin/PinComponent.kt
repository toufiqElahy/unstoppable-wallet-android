package io.horizontalsystems.pin

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISecuredStorage
import io.horizontalsystems.pin.core.LockManager
import io.horizontalsystems.pin.core.PinManager

class PinComponent(
        application: Application,
        securedStorage: ISecuredStorage,
        private val excludedActivityNames: List<String>,
        private val onFire: (activity: Activity, requestCode: Int) -> Unit
) : Application.ActivityLifecycleCallbacks, IPinComponent {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    private var refs: Int = 0
    private var foregroundActivity: Activity? = null
    private val pinManager = PinManager(securedStorage)
    private val appLockManager = LockManager(pinManager, application.applicationContext)

    //IPinComponent

    override var isFingerprintEnabled: Boolean
        get() = pinManager.isFingerprintEnabled
        set(value) {
            pinManager.isFingerprintEnabled = value
        }

    override val isPinSet: Boolean
        get() = pinManager.isPinSet

    override fun store(pin: String) {
        pinManager.store(pin)
    }

    override fun validate(pin: String): Boolean {
        return pinManager.validate(pin)
    }

    override fun clear() {
        pinManager.clear()
    }

    override fun onUnlock() {
        appLockManager.onUnlock()
    }

    override fun updateLastExitDateBeforeRestart() {
        appLockManager.updateLastExitDate()
    }

    //Application.ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityResumed(activity: Activity) {
        foregroundActivity = activity

        if (appLockManager.isLocked && !excludedActivityNames.contains(activity::class.java.name)) {
            onFire.invoke(activity, 1)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        foregroundActivity = null
    }

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityStarted(activity: Activity) {
        if (refs == 0) {
            appLockManager.willEnterForeground()
        }
        refs++
    }

    override fun onActivityStopped(activity: Activity) {
        refs--

        if (refs == 0) {
            appLockManager.didEnterBackground()
        }
    }

}
