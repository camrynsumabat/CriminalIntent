package android.bignerdranch.photogallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.util.concurrent.ConcurrentHashMap

//purpose: download and serve images to PhotoGalleryFragment

private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0  //used to ID messages as download requests

//T used for generic, more flexible implementation
class ThumbnailDownloader<in T>(
    private val responseHandler: Handler,
    private val onThumbnailDownloaded: (T, Bitmap) -> Unit) : HandlerThread(TAG) {

    //making ThumbnailDownloader into a lifecycle aware component
    //implementing LifeCycleObserver interface to observe onCreate & onDestroy functions
    val fragmentLifecycleObserver: LifecycleObserver = object : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
        fun setup() {
            Log.i(TAG, "Starting background thread")
            start()
            looper
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun tearDown() {
            Log.i(TAG, "Destroying background thread")
            quit() //always make sure to quit or HandlerThreads will never die
        }
    }

    val viewLifeCycleObserver: LifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun clearQueue() {
            Log.i(TAG, "Clearing all requests from queue")
            requestHandler.removeMessages(MESSAGE_DOWNLOAD)
            requestMap.clear()
        }
    }

    private var hasQuit = false
    private lateinit var requestHandler: Handler    //store a reference to the Handler responsible for queueing
    private val requestMap = ConcurrentHashMap<T, String>() //retrieve URL associated with a particular request
    private val flickrFetchr = FlickrFetchr()   //makes sure the Retrofit setup code only executes once

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("HandlerLeak")
    override fun onLooperPrepared() {
        requestHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if(msg.what == MESSAGE_DOWNLOAD) {
                    val target = msg.obj as T
                    Log.i(TAG, "Got a request for URL: ${requestMap[target]}")
                    handleRequest(target)
                }
            }

        }
    }

    override fun quit(): Boolean {
        hasQuit = true
        return super.quit()
    }

    //called in PhotoAdapter.kt onBindViewHolder(...)
    fun queueThumbnail(target: T, url:String) {
        Log.i(TAG, "Got a URL: $url")
        requestMap[target] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget()
    }

    private fun handleRequest(target: T) {
        val url = requestMap[target] ?: return
        val bitmap = flickrFetchr.fetchPhoto(url) ?: return

        //bc this is with the looper, all the code in run() is executed on the main thread
        responseHandler.post(Runnable {
            if (requestMap[target] != url || hasQuit) {
                return@Runnable
            }

            requestMap.remove(target)
            onThumbnailDownloaded(target, bitmap)
        })
    }

}