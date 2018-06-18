package bitcoin.wallet

import android.support.v7.app.AppCompatActivity
import android.util.Log
import bitcoin.wallet.lib.WalletDataManager
import bitcoin.wallet.modules.main.MainModule
import bitcoin.wallet.modules.guest.GuestModule

class LauncherActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()

        if (WalletDataManager.hasWallet()) {
            MainModule.start(this)
        } else {
            GuestModule.start(this)
        }

        finish()
    }

}

fun Any?.log(label: String = "") {

    Log.e("AAA", "$label: $this")

}