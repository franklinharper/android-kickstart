package com.franklinharper.kickstart

import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import com.franklinharper.kickstart.network.LaMetroApi
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val loggingInterceptor = HttpLoggingInterceptor().also { it.level =  HttpLoggingInterceptor.Level.BODY};
    private val clientBuilder = OkHttpClient.Builder().also { it.addInterceptor(loggingInterceptor) };

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.metro.net/")
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .client(clientBuilder.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val laMetroApi = retrofit.create(LaMetroApi::class.java)

    private val compositeDisposable = CompositeDisposable()

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                message.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                message.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressIndicator.visibility = View.VISIBLE
        val disposable = laMetroApi
            .listAgencies()
            .subscribeOn(Schedulers.io())
            // For demo purposes, delay to ensure that the spinner has time to show and animate
            .delay(1000, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { agencies ->
                    progressIndicator.visibility = View.GONE
                    message.text = agencies.joinToString(separator = "\n")
                    agencies.forEach {
                        Log.d("TAG", it.toString())
                    }
                }, { throwable ->
                    progressIndicator.visibility = View.GONE
                    // Crash asap to make development easier.
                    throw throwable
                }
            )
        compositeDisposable.add(disposable)
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }
}
