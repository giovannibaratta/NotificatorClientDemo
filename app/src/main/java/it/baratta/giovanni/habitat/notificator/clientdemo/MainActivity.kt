package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.RxLifecycle
import com.trello.rxlifecycle2.android.ActivityEvent
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : RxAppCompatActivity(), IMainView {

    private lateinit var presenter: IMainPresenter

    private val interactiveComponent by lazy { arrayOf(activityMainMQTTTopic,
                                                activityMainRegistrationServer,
                                                activityMainMQTTSwitch,
                                                activityMainFCMSwitch,
                                                activityMainMQTTServer,
                                                activityMainSEPASwitch,
                                                activityMainSEPAQuery,
                                                activityMainPingSwitch
                                                )}

    /* INPUT */

    override val mqttEnabled: Boolean
        get() = activityMainMQTTSwitch.isChecked

    override val mqttServer: String
        get() = activityMainMQTTServer.text.toString()

    override val fcmEnabled: Boolean
        get() = activityMainFCMSwitch.isChecked

    override val fcmServer: String
        get() = activityFCMServer.text.toString()

    override val mqttTopic: String
        get() = activityMainMQTTTopic.text.toString()

    override var registrationServer: String
        get() = activityMainRegistrationServer.text.toString()
        set(value) = activityMainRegistrationServer.setText(value)

    override val sepaEnabled: Boolean
        get() = activityMainSEPASwitch.isChecked

    override val pingEnabled: Boolean
        get() = activityMainPingSwitch.isChecked

    override val sepaServer: String
        get() = activityMainSEPAServer.text.toString()

    override val sepaQuery: String
        get() = activityMainSEPAQuery.text.toString()

    /* UI */

    override var registered: Boolean = false
        get() = field
        set(value) {
            field = value
            when (value) {
                true -> {
                    activityMainRegistrationStatus.text = "STATO REGISTRAZIONE : REGISTRATO"
                    activityMainRegistrationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                }
                false -> {
                    activityMainRegistrationStatus.text = "STATO REGISTRAZIONE : NON  REGISTRATO"
                    activityMainRegistrationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                }
            }
        }

    override var registerButtonEnabled: Boolean = true
        get() = field
        set(value) {
            field = value
            activityMainRegistrationButton.isEnabled = field
        }

    override var deregisterButtonEnabled: Boolean = false
        get() = field
        set(value) {
            field = value
            activityMainDeregistrationButton.isEnabled = field
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registrationServerChanged = RxTextView.afterTextChangeEvents(activityMainRegistrationServer)
                .map { event -> event.view().text.toString() }
                .compose(RxLifecycle.bindUntilEvent(lifecycle(), ActivityEvent.DESTROY))


        activityMainRegistrationButton.setOnClickListener { presenter.register() }
        activityMainDeregistrationButton.setOnClickListener { presenter.deregister() }
        activityMainMQTTSwitch.isChecked = true
        activityMainMQTTServer.setText("tcp://192.168.0.5:1883")
        activityMainMQTTTopic.setText("HabitatDevice")
        activityMainRegistrationServer.setText("192.168.0.5:8080/core_main_Web_exploded/")

        presenter = MainPresenter(this)
    }

    override fun showUI(show: Boolean) {
        for (i in 0.until(activityMainContainer.childCount))
            when (show) {
                true -> activityMainContainer.getChildAt(i).visibility = View.VISIBLE
                false -> activityMainContainer.getChildAt(i).visibility = View.GONE
            }
        activityMainConnessionMessage.visibility = View.GONE
    }

    override fun showProgress(show: Boolean) {
        when (show) {
            true -> {
                activityMainProgressBar.visibility = View.VISIBLE
                activityMainConnessionMessage.visibility = View.VISIBLE
            }
            false -> {
                activityMainProgressBar.visibility = View.GONE
                activityMainConnessionMessage.visibility = View.GONE
            }
        }
    }

    override fun showError(msg: String) {
        val snackbar = Snackbar.make(activityMainContainer, msg, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.getView()
        val snackbarTextId = android.support.design.R.id.snackbar_text
        val textView = snackbarView.findViewById<TextView>(snackbarTextId)
        textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        snackbar.show()
    }

    override fun showMessagge(msg: String) {
        val snackbar = Snackbar.make(activityMainContainer, msg, Snackbar.LENGTH_LONG).show()
    }

    override var registrationServerEnabled: Boolean = true
        get() = field
        set(value) {
            field = value
            activityMainRegistrationServer.isEnabled = value
        }

    override val context: Context
        get() = this

    override fun onRestart() {
        super.onRestart()
        presenter.onRestart()
    }

    override fun onPause() {
        presenter.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        presenter.onDesroy()
        super.onDestroy()
    }

    override fun showRegistrationStatusProgress(show: Boolean) {
        when(show){
            true -> activityMainRegistrationStatusProgress.visibility = View.VISIBLE
            false -> activityMainRegistrationStatusProgress.visibility = View.INVISIBLE
        }
    }

    override lateinit var registrationServerChanged: Observable<String>
        private set

    override fun lockInteractiveComponents(lock: Boolean)
        = interactiveComponent.forEach { it.isEnabled = !lock }

}