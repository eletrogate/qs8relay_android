package fsiles.name.qsrelay.feature.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.util.Log
import fsiles.name.qsrelay.feature.store.DeviceData
import fsiles.name.qsrelay.feature.store.RelayData
import fsiles.name.qsrelay.feature.store.StoreUtils
import fsiles.name.qsrelay.feature.utils.RelaysUtils
import java.math.BigInteger
import java.util.ArrayList


class JobUtils{

    companion object {
        private const val MIN_LATENCY_RAPID_MODE: Long = 1 * 1000 //ms
        private const val MAX_OVERRIDE_DEADLINE_RAPID_MODE: Long = 3 * 1000 //ms
        private const val MIN_LATENCY_NOT_RAPID_MODE: Long = 25 * 1000 //ms
        private const val MAX_OVERRIDE_DEADLINE_NOT_RAPID_MODE: Long = 35 * 1000 //ms

        fun scheduleJob(context: Context, rapid: Boolean) {
            val serviceComponent = ComponentName(context, AutoUpdateJobService::class.java)
            val builder = JobInfo.Builder(0, serviceComponent)
            if(rapid){
                builder.setMinimumLatency(MIN_LATENCY_RAPID_MODE) // wait at least
                builder.setOverrideDeadline(MAX_OVERRIDE_DEADLINE_RAPID_MODE) // maximum delay
            }else{
                builder.setMinimumLatency(MIN_LATENCY_NOT_RAPID_MODE) // wait at least
                builder.setOverrideDeadline(MAX_OVERRIDE_DEADLINE_NOT_RAPID_MODE) // maximum delay
            }
            //builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED); // require unmetered network
            //builder.setRequiresDeviceIdle(true); // device should be idle
            //builder.setRequiresCharging(false); // we don't care if the device is charging or not
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            jobScheduler.schedule(builder.build())
        }

        fun processService(context: Context){
            try{
                val devicesStores = StoreUtils.getDevicesStore(context)
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if(adapter != null && adapter.isEnabled){
                    adapter.cancelDiscovery()
                    if(devicesStores!=null && !devicesStores.devices.isEmpty()){
                        for(address in devicesStores.devices.keys){
                            val deviceData = devicesStores.devices[address]
                            if(deviceData!=null){
                                processDevice(adapter, address, deviceData)
                            }
                        }
                    }
                }
            }catch (e: Exception){
                Log.e("AutoUpdateService","A error occurred when processService.", e)
            }
        }

        private fun processDevice(bAdapter: BluetoothAdapter, mAddress:String, deviceData: DeviceData){
            try{
                if(!deviceData.relays.isEmpty()){
                    val commands = ArrayList<ByteArray>()
                    val commandIndex = ArrayList<Int>()
                    for(relay in deviceData.relays){
                        if(checkRelay(relay)) {
                            commands.add(RelaysUtils.getCommand(relay.index, relay.state))
                            commandIndex.add(relay.index)
                        }
                    }
                    val maxTries = deviceData.maxTries
                    val msTimeBetweenEachTry = deviceData.msTimeBetweenEachTry
                    val results = RelaysUtils.sendCommands(bAdapter, mAddress,
                            maxTries, msTimeBetweenEachTry, commands)
                    for(i in 0 until results.size){
                        if(!results[i]){
                            Log.e("AutoUpdateService",
                                    "Failed to send a command with index:"+commandIndex[i]
                                            + " and command "
                                            + getHexadecimalString(commands[i]))
                        }
                    }
                }
            }catch (e: Exception){
                Log.e("AutoUpdateService",
                        "A error occurred when processDevice: $mAddress.",
                        e)
            }
        }

        private fun checkRelay(relay: RelayData): Boolean {
            var toReturn = true
            if(relay.index<0){
                toReturn = false
            }
            return toReturn
        }

        private fun getHexadecimalString(command: ByteArray): String{
            return BigInteger(1, command).toString(16).toUpperCase()
        }

    }
}