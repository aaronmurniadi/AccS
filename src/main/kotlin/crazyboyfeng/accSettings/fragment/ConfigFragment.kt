package com.acc.settings.fragment

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.acc.settings.R
import com.acc.settings.acc.Command
import com.acc.settings.data.ConfigDataStore
import crazyboyfeng.android.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("unused")
class ConfigFragment : PreferenceFragmentCompat() {
    private lateinit var shutdownCapacity: NumberPickerPreference
    private lateinit var cooldownCapacity: NumberPickerPreference
    private lateinit var resumeCapacity: NumberPickerPreference
    private lateinit var pauseCapacity: NumberPickerPreference
    private lateinit var capacityMask: SwitchPreference
    private lateinit var supportInVoltage: SwitchPreference
    private lateinit var cooldownTemp: NumberPickerPreference
    private lateinit var maxTemp: NumberPickerPreference
    private lateinit var shutdownTemp: NumberPickerPreference
    private lateinit var cooldownCharge: EditTextPreferencePlus
    private lateinit var cooldownPause: EditTextPreferencePlus
    private lateinit var cooldownCustom: EditTextPreference
    private lateinit var maxChargingVoltage: EditTextPreferencePlus
    private lateinit var prioritizeBattIdleMode: SwitchPreference
    private lateinit var chargingSwitch: EditTextPreference
    private lateinit var currentWorkaround: SwitchPreference
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val configDataStore = ConfigDataStore(requireContext())
        preferenceManager.preferenceDataStore = configDataStore

        setPreferencesFromResource(R.xml.config_preferences, rootKey)
        shutdownCapacity = findPreference(getString(R.string.set_shutdown_capacity))!!
        cooldownCapacity = findPreference(getString(R.string.set_cooldown_capacity))!!
        resumeCapacity = findPreference(getString(R.string.set_resume_capacity))!!
        pauseCapacity = findPreference(getString(R.string.set_pause_capacity))!!
        capacityMask = findPreference(getString(R.string.set_capacity_mask))!!
        supportInVoltage = findPreference(getString(R.string.support_in_voltage))!!
        cooldownTemp = findPreference(getString(R.string.set_cooldown_temp))!!
        maxTemp = findPreference(getString(R.string.set_max_temp))!!
        shutdownTemp = findPreference(getString(R.string.set_shutdown_temp))!!
        cooldownCharge = findPreference(getString(R.string.set_cooldown_charge))!!
        cooldownPause = findPreference(getString(R.string.set_cooldown_pause))!!
        cooldownCustom = findPreference(getString(R.string.set_cooldown_custom))!!
        maxChargingVoltage = findPreference(getString(R.string.set_max_charging_voltage))!!
        prioritizeBattIdleMode = findPreference(getString(R.string.set_prioritize_batt_idle_mode))!!
        chargingSwitch = findPreference(getString(R.string.set_charging_switch))!!
        currentWorkaround = findPreference(getString(R.string.set_current_workaround))!!

        configDataStore.onConfigChangeListener = ConfigDataStore.OnConfigChangeListener {
            when (it) {
                shutdownCapacity.key -> onShutdownCapacitySet()
                cooldownCapacity.key -> onMiddleCapacitySet()
                resumeCapacity.key -> onMiddleCapacitySet()
                pauseCapacity.key -> onPauseCapacitySet()
                supportInVoltage.key -> onSupportInVoltageSet()
                cooldownTemp.key -> onCooldownTempSet()
                maxTemp.key -> onMaxTempSet()
                shutdownTemp.key -> onShutdownTempSet()
                cooldownCharge.key -> onCooldownChargeSet()
                cooldownPause.key -> onCooldownPauseSet()
                cooldownCustom.key -> onCooldownCustomSet()
                chargingSwitch.key -> onChargingSwitchChanged()
                currentWorkaround.key -> onCurrentWorkaroundChanged()
            }
        }

        val capacitySummaryProvider = Preference.SummaryProvider<NumberPickerPreference> {
            when (val value = it.value) {
                in 0..100 -> "$value %"
                in VOLT_MIN..VOLT_MAX -> "$value mV"
                else -> getString(androidx.preference.R.string.not_set)
            }
        }
        shutdownCapacity.summaryProvider = capacitySummaryProvider
        cooldownCapacity.summaryProvider = capacitySummaryProvider
        resumeCapacity.summaryProvider = capacitySummaryProvider
        pauseCapacity.summaryProvider = capacitySummaryProvider
        val capacityOnBindNumberPickerListener = NumberPickerPreference.OnBindNumberPickerListener {
            it.setOnValueChangedListener { picker, oldVal, newVal ->
                if (oldVal == 100 && newVal == 100 + 1) {
                    picker.value = VOLT_MIN
                } else if (oldVal == VOLT_MIN && newVal == VOLT_MIN - 1) {
                    picker.value = 100
                }
                val valid = newVal in 0..100 || newVal in VOLT_MIN..VOLT_MAX
                picker.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = valid
            }
        }
        shutdownCapacity.onBindNumberPickerListener = capacityOnBindNumberPickerListener
        cooldownCapacity.onBindNumberPickerListener = capacityOnBindNumberPickerListener
        resumeCapacity.onBindNumberPickerListener = capacityOnBindNumberPickerListener
        pauseCapacity.onBindNumberPickerListener = capacityOnBindNumberPickerListener
        onSupportInVoltageSet()

        onCooldownTempSet()
        onMaxTempSet()
        onShutdownTempSet()

        onCooldownChargeSet()
        onCooldownPauseSet()
        onCooldownCustomSet()

        maxChargingVoltage.setOnBindEditTextListener {
            it.doOnTextChanged { text, _, _, _ ->
                it.error =
                    if (text.isNullOrEmpty() || text.toString().toInt() in VOLT_AVG..VOLT_MAX) {
                        null
                    } else {
                        getString(R.string.hint_between, VOLT_AVG, VOLT_MAX)
                    }
                it.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled =
                    it.error.isNullOrEmpty()
            }
        }

        chargingSwitch.setSummaryProvider {
            val text = (it as EditTextPreference).text
            if (text.isNullOrEmpty()) {
                getString(androidx.preference.R.string.not_set)
            } else when (text.toIntOrNull()) {
                in 0 until VOLT_AVG -> "$text mA"
                in VOLT_AVG..VOLT_MAX -> "$text mV"
                else -> text
            }
        }
        onChargingSwitchSet()

        loadDefault()

    }

    private fun inVoltage(preference: NumberPickerPreference) =
        preference.value in VOLT_MIN..VOLT_MAX

    private fun capacitiesInVoltage(): Boolean {
        val shutdownInVoltage = inVoltage(shutdownCapacity)
        val cooldownInVoltage = inVoltage(cooldownCapacity)
        val resumeInVoltage = inVoltage(resumeCapacity)
        val pauseInVoltage = inVoltage(pauseCapacity)
        val inVoltage = shutdownInVoltage || cooldownInVoltage || resumeInVoltage || pauseInVoltage
        if (inVoltage) {
            supportInVoltage.isChecked = true
        }
        supportInVoltage.isEnabled = !inVoltage
        return supportInVoltage.isChecked
    }

    private fun onShutdownCapacitySet() {
        if (capacitiesInVoltage()) {
            return
        }
        val value = shutdownCapacity.value
        resumeCapacity.minValue = value + 1
    }

    private fun onMiddleCapacitySet() {
        if (capacitiesInVoltage()) {
            return
        }
        val cooldownCapacityValue = cooldownCapacity.value
        val resumeCapacityValue = resumeCapacity.value
        shutdownCapacity.maxValue =
            (if (cooldownCapacityValue < resumeCapacityValue) cooldownCapacityValue else resumeCapacityValue) - 1
        pauseCapacity.minValue =
            (if (cooldownCapacityValue > resumeCapacityValue) cooldownCapacityValue else resumeCapacityValue) + 1
    }

    private fun onPauseCapacitySet() {
        capacityMask.isEnabled = !inVoltage(pauseCapacity)
        if (capacitiesInVoltage()) {
            return
        }
        val value = pauseCapacity.value
        cooldownCapacity.maxValue = value - 1
        resumeCapacity.maxValue = value - 1
    }

    private fun onSupportInVoltageSet() {
        if (supportInVoltage.isChecked) {
            shutdownCapacity.maxValue = VOLT_MAX
            cooldownCapacity.minValue = 0
            cooldownCapacity.maxValue = VOLT_MAX
            resumeCapacity.minValue = 0
            resumeCapacity.maxValue = VOLT_MAX
            pauseCapacity.minValue = 0
            pauseCapacity.maxValue = VOLT_MAX
        } else {
            pauseCapacity.maxValue = 100
            onShutdownCapacitySet()
            onMiddleCapacitySet()
            onPauseCapacitySet()
        }
    }

    private fun onCooldownTempSet() {
        maxTemp.minValue = cooldownTemp.value + 1
    }

    private fun onMaxTempSet() {
        val value = maxTemp.value
        cooldownTemp.maxValue = value - 1
        shutdownTemp.minValue = value + 1
    }

    private fun onShutdownTempSet() {
        maxTemp.maxValue = shutdownTemp.value - 1
    }

    private fun onCooldownChargeSet() {
        val isValueEmpty = cooldownCharge.text.isNullOrEmpty()
        val isCooldownPauseEmpty = cooldownPause.text.isNullOrEmpty()
        cooldownCustom.isEnabled = isValueEmpty && isCooldownPauseEmpty
    }

    private fun onCooldownPauseSet() {
        val isCooldownChargeEmpty = cooldownCharge.text.isNullOrEmpty()
        val isValueEmpty = cooldownPause.text.isNullOrEmpty()
        cooldownCustom.isEnabled = isCooldownChargeEmpty && isValueEmpty
    }

    private fun onCooldownCustomSet() {
        val isValueEmpty = cooldownCustom.text.isNullOrEmpty()
        cooldownCharge.isEnabled = isValueEmpty
        cooldownPause.isEnabled = isValueEmpty
    }

    private fun onChargingSwitchSet() {
        prioritizeBattIdleMode.isEnabled = chargingSwitch.text.isNullOrEmpty()
    }

    private fun onChargingSwitchChanged() {
        onChargingSwitchSet()
        CoroutineScope(Dispatchers.Default).launch { Command.restartDaemon() }
    }

    private fun onCurrentWorkaroundChanged() {
        CoroutineScope(Dispatchers.Default).launch { Command.reinitialize() }
    }

    private fun loadDefault() = lifecycleScope.launchWhenCreated {
        val properties = Command.getDefaultConfig()
        Log.d(TAG, "loadDefault ${properties.size}")
        for (property in properties) {
            val value = property.value as String
            if (value.isEmpty()) {
                continue
            }// value not empty
            val key = property.key as String
            when (val preference = findPreference<Preference>(key)) {
                is NumberPickerPreference -> preference.setDefaultValue(value.toInt())
            }
        }
    }

    private companion object {
        const val TAG = "ConfigFragment"
        const val VOLT_MIN = 3000
        const val VOLT_AVG = 3700
        const val VOLT_MAX = 4200
    }
}