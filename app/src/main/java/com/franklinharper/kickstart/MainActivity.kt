package com.franklinharper.kickstart

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.franklinharper.kickstart.network.LaMetroApi
import com.google.android.material.snackbar.Snackbar
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.rx.asObservable
import com.squareup.sqldelight.runtime.rx.mapToList
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private val logTag = MainActivity::class.java.name

    private val compositeDisposable = CompositeDisposable()

    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, this, "transport.db")
    private val database = Database(driver)
    private val agencyQueries: AgencyQueries = database.agencyQueries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        observeDb()
        refreshDbFromNetwork()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    private fun observeDb() {
        agencyQueries
            .selectAll()
            .asObservable(Schedulers.io())
            .mapToList()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ agency ->
                Log.d(logTag, "displayed: $agency")
                message.text = agency.toString()
            }, { throwable ->
                // Crash asap to make development easier.
                // TODO productionize crash handling (e.g. use Crashlytics)
                throw throwable
            }).also { compositeDisposable.addAll(it) }
    }

    private fun refreshDbFromNetwork() {
        val loggingInterceptor = HttpLoggingInterceptor().also { it.level = HttpLoggingInterceptor.Level.BODY }
        val clientBuilder = OkHttpClient.Builder().also { it.addInterceptor(loggingInterceptor) }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.metro.net/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val laMetroApi = retrofit.create(LaMetroApi::class.java)
        progressIndicator.visibility = View.VISIBLE
        laMetroApi
            .listAgencies()
            .subscribeOn(Schedulers.io())
            // For demo purposes, delay to ensure that the spinner has time to show and animate
            //  .delay(1000, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { agencies ->
                    progressIndicator.visibility = View.GONE
                    agencies.forEach {
                        Log.d(logTag, "received: $it")
                        agencyQueries.insertOrReplace(id = it.id, name = it.name)
                    }
                }, { throwable ->
                    progressIndicator.visibility = View.GONE
                    when (throwable) {
                        is UnknownHostException -> {
                            Snackbar.make(message, "Unable to connect to server", Snackbar.LENGTH_SHORT).show();
                        }
                        else ->
                            // Crash asap to make development easier.
                            // TODO productionize crash handling (e.g. use Crashlytics)
                            throw throwable
                    }
                }
            ).also { compositeDisposable.add(it) }
    }
}
