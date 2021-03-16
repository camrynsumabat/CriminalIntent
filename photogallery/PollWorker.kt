package android.bignerdranch.photogallery

import android.app.DownloadManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Gallery
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

private const val TAG = "PollWorker"

//only knows how to execute background work, WorkRequest will schedule PollWorker (check PhotoGalleryFragment.kt)
class PollWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    //doWork() is called from background thread, any long-running tasks can be done here
    //can return failure (will not run again) or success
    override fun doWork(): Result {
        val query = QueryPreferences.getStoredQuery(context)
        val lastResultId = QueryPreferences.getLastResultId(context)

        //no search query, return regular photos
        //search query, perform search request
        //empty list if either fails
        val items: List<GalleryItem> = if (query.isEmpty()) {
            FlickrFetchr().fetchPhotosRequest()
                .execute()
                .body()
                ?.photos
                ?.galleryItems
        } else {
            FlickrFetchr().searchPhotosRequest(query)
                .execute()
                .body()
                ?.photos
                ?.galleryItems
        } ?: emptyList()

        if(items.isEmpty()) {
            return Result.success()
        }

        val resultId = items.first().id
        if (resultId == lastResultId) {
            Log.i(TAG, "Got an old result: $resultId")
        } else {
            Log.i(TAG, "Got a new result: $resultId")
            QueryPreferences.setLastResultId(context, resultId)

            val intent = PhotoGalleryActivity.newIntent(context)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
            val resources = context.resources

            //used to support pre-Oreo and Oreo+ devices
            val notification = NotificationCompat
                .Builder(context, NOTIFICATION_CHANNEL_ID)  //uses ID to set notif channel if Oreo+, else ignores
                .setTicker(resources.getString(R.string.new_pictures_title))    //config ticker text
                .setSmallIcon(android.R.drawable.ic_menu_report_image)  //config icon

                    //config appearance of notif
                .setContentTitle(resources.getString(R.string.new_pictures_title))
                .setContentText(resources.getString(R.string.new_pictures_text))
                .setContentIntent(pendingIntent)    //specify what happens when the user presses notif
                .setAutoCancel(true)
                .build()
            /*val notificationManager = NotificationManagerCompat.from(context)   //post notif
            notificationManager.notify(0, notification) //identifies and passes notif

            context.sendBroadcast(Intent(ACTION_SHOW_NOTIFICATION), PERM_PRIVATE)
             */

            showBackgroundNotification(0, notification)
        }

        return Result.success()
    }

    private fun showBackgroundNotification(
        requestCode: Int,
        notification: Notification
    ) {
        val intent = Intent(ACTION_SHOW_NOTIFICATION).apply {
            putExtra(REQUEST_CODE, requestCode)
            putExtra(NOTIFICATION, notification)
        }
        context.sendOrderedBroadcast(intent, PERM_PRIVATE)
    }
    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.bignerdranch.android.photogallery.SHOW_NOTIFICATION"
        const val PERM_PRIVATE = "com.bignerdranch.android.photogallery.PRIVATE"
        const val REQUEST_CODE = "REQUEST_CODE"
        const val NOTIFICATION = "NOTIFICATION"
    }
}