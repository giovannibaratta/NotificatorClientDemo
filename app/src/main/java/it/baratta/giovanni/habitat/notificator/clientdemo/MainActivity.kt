package it.baratta.giovanni.habitat.notificator.clientdemo

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), IMainView{

    private lateinit var presenter : IMainPresenter

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
        presenter = MainPresenter(this)

        activityMainRegistrationButton.setOnClickListener{ presenter.register()}
        activityMainDeregistrationButton.setOnClickListener{ presenter.deregister()}
        activityMainMQTTSwitch.isChecked = true
        activityMainMQTTServer.setText("tcp://192.168.0.5:1883")
        activityMainMQTTTopic.setText("HabitatDevice")
        activityMainRegistrationServer.setText("192.168.0.5")
        activityMainRegistrationServerPort.setText("8080")
    }

    override fun showUI(show: Boolean) {
        for (i in 0.until(activityMainContainer.childCount))
            when (show) {
                true -> activityMainContainer.getChildAt(i).visibility = View.VISIBLE
                false -> activityMainContainer.getChildAt(i).visibility = View.GONE
            }
        activityMainConnessionMessage.visibility = View.GONE
    }

    override fun showProgress(show: Boolean){
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

    override val mqttEnabled: Boolean
        get() = activityMainMQTTSwitch.isChecked

    override val mqttServer: String
        get() = activityMainMQTTServer.text.toString()

    override val fcmEnabled: Boolean
        get() = activityMainFCMSwitch.isChecked

    override fun showError(msg: String) {
        val snackbar = Snackbar.make(activityMainContainer, msg, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.getView()
        val snackbarTextId = android.support.design.R.id.snackbar_text
        val textView = snackbarView.findViewById<TextView>(snackbarTextId)
        textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        snackbar.show()
    }

    override val mqttTopic: String
        get() = activityMainMQTTTopic.text.toString()

    override fun showMessagge(msg: String) {
        val snackbar = Snackbar.make(activityMainContainer, msg, Snackbar.LENGTH_LONG).show()
    }

    override val registrationServer: String
        get() = activityMainRegistrationServer.text.toString()

    override val registrationPort: Int?
        get() = activityMainRegistrationServerPort.text.toString().toIntOrNull()

    override var registrationServerEnabled: Boolean = true
        get() = field
        set(value) {
            field = value
            activityMainRegistrationServer.isEnabled = value
        }

    override var registrationServerPortEnabled: Boolean = true
        get() = field
        set(value) {
            field = value
            activityMainRegistrationServerPort.isEnabled = value
        }
}
