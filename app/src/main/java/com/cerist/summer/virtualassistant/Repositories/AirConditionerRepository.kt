package com.cerist.summer.virtualassistant.Repositories

import android.util.Log
import com.cerist.summer.virtualassistant.Entities.BroadLinkProfile
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.Executor


class AirConditionerRepository(private val broadLink: Observable<RxBleDevice>,
                               private val bluetoothExecutor: Executor) : IRepository {

    companion object {
        private val TAG = "AirConditionerRepo"
    }

     val broadLinkConnection: Observable<RxBleConnection>
     val broadLinkConnectionState:Observable<RxBleConnection.RxBleConnectionState>

     val airConditionerPowerState:Observable<BroadLinkProfile.AirConditionerProfile.State>
     val airConditionerMode:Observable<BroadLinkProfile.AirConditionerProfile.Mode>
     val airConditionerTemp:Observable<Int>

    init {
        broadLinkConnection =  broadLink.observeOn(Schedulers.from(bluetoothExecutor))
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    Log.d(TAG,"connecting to the GATT server named ${it.name}")
                    it.establishConnection(true) }
                .retry()
                .share()

        broadLinkConnectionState =  broadLink.observeOn(Schedulers.from(bluetoothExecutor))
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    Log.d(TAG,"start to observe the connection state with ${it.name}: ${it.connectionState.name}")
                    it.observeConnectionStateChanges()
                }
                .share()


        airConditionerPowerState =   broadLinkConnection.observeOn(Schedulers.from(bluetoothExecutor))
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    Log.d(TAG,"reading from the Characteristic")
                    it.readCharacteristic(UUID.fromString(BroadLinkProfile.AirConditionerProfile.STATE_CHARACTERISTIC_UUID))
                            .toObservable()}
                .flatMap {
                    Observable.just(it[0].toInt()) }
                .flatMap {
                        when (it) {
                            0 -> Observable.just(BroadLinkProfile.AirConditionerProfile.State.OFF)
                            1 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.State.ON)
                            else -> Observable.error(Throwable("unknown value"))
                    }}
                .share()

        airConditionerMode = broadLinkConnection.observeOn(Schedulers.from(bluetoothExecutor))
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    it.readCharacteristic(UUID.fromString(BroadLinkProfile.AirConditionerProfile.MODE_CHARACTERISTIC_UUID))
                            .toObservable()}
                .flatMap {
                    Observable.just(it[0].toInt()) }
                .flatMap {
                        when (it) {
                            0 -> Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.SLEEP)
                            1 -> Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.ENERGY_SAVER)
                            2 -> Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.FUN)
                            3 -> Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.COOL)
                            else -> Observable.error(Throwable("unknown value"))
                    }}
                .share()


        airConditionerTemp = broadLinkConnection.observeOn(Schedulers.from(bluetoothExecutor))
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    it.readCharacteristic(UUID.fromString(BroadLinkProfile.AirConditionerProfile.TEMPERATURE_UP_CHARACTERISTIC_UUID))
                            .toObservable()}
                .flatMap {
                    Observable.just(it[0].toInt()) }
                .flatMap {
                        if(it in BroadLinkProfile.AirConditionerProfile.MIN_TEMP
                                    .. BroadLinkProfile.AirConditionerProfile.MAX_TEMP)
                            Observable.just(it)
                        else
                            Observable.error(Throwable("inappropriate value"))
                    }
                .share()

    }



    fun setAirConditionerPowerState(state: BroadLinkProfile.AirConditionerProfile.State)
           =  broadLinkConnection
                .observeOn(Schedulers.from(bluetoothExecutor))
                .flatMap {
                    it.writeCharacteristic(UUID.fromString( BroadLinkProfile.AirConditionerProfile.STATE_CHARACTERISTIC_UUID), byteArrayOf(state.value.toByte())).toObservable()
                }
                .flatMap {
                       Observable.just(it[0].toInt())

                }
                .flatMap {
                        when (it) {
                            0 -> Observable.just(BroadLinkProfile.AirConditionerProfile.State.OFF)
                            1 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.State.ON)
                            else -> Observable.error(Throwable("unknown value"))
                        }
                }
                .share()!!



    fun setAirConditionerMode(mode:BroadLinkProfile.AirConditionerProfile.Mode)
           =  broadLinkConnection
            .observeOn(Schedulers.from(bluetoothExecutor))
            .flatMap {
                it.writeCharacteristic(UUID.fromString( BroadLinkProfile.AirConditionerProfile.MODE_CHARACTERISTIC_UUID), byteArrayOf(mode.value.toByte())).toObservable()
            }
            .flatMap {
                Observable.just(it[0].toInt())
            }
            .flatMap {
                    when (it) {
                        0 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.SLEEP)
                        1 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.ENERGY_SAVER)
                        2 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.FUN)
                        3 ->  Observable.just(BroadLinkProfile.AirConditionerProfile.Mode.COOL)
                        else -> Observable.error(Throwable("unknown value"))
                    }}
            .share()!!

    fun setAirConditionerTemp(temperature:Int)
            =  broadLinkConnection
            .observeOn(Schedulers.from(bluetoothExecutor))
            .flatMap {
                if(temperature in BroadLinkProfile.AirConditionerProfile.MIN_TEMP
                        .. BroadLinkProfile.AirConditionerProfile.MAX_TEMP)
                    Observable.just(it)
                else
                    Observable.error(Throwable("inappropriate value"))
            }
            .flatMap { airConditionerTemp }
            .flatMap {
                if(temperature > it)
                    broadLinkConnection.blockingLast().writeCharacteristic(UUID.fromString( BroadLinkProfile.AirConditionerProfile.TEMPERATURE_UP_CHARACTERISTIC_UUID),
                            byteArrayOf(temperature.toByte())).toObservable()
                else
                    broadLinkConnection.blockingLast().writeCharacteristic(UUID.fromString( BroadLinkProfile.AirConditionerProfile.TEMPERATURE_DOWN_CHARACTERISTIC_UUID),
                            byteArrayOf(temperature.toByte())).toObservable()
            }
            .flatMap{
                Observable.just(it[0].toInt())
            }
            .flatMap { value ->
                Observable.just(value)
            }
            .share()!!


}