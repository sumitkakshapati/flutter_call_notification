
package com.timus.call_notification

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.timus.call_notification.models.NotificationData
import com.timus.call_notification.models.ReceivedNotificationData
import com.timus.call_notification.utils.NotificationBuilder
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.NewIntentListener
import java.util.*
import kotlin.concurrent.timerTask


/** CallNotificationPlugin */
class CallNotificationPlugin: FlutterPlugin, MethodCallHandler,NewIntentListener,ActivityAware,Application.ActivityLifecycleCallbacks{
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  lateinit var applicationContext : Context
  private lateinit var foregroundIntent : Intent
  private var initialActivity: Activity? = null
  private var unHandledActionIntent : Intent? = null
  private var notificationTimer : Timer? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "call_notification")
    channel.setMethodCallHandler(this)
    applicationContext = flutterPluginBinding.applicationContext;
    foregroundIntent = Intent(applicationContext,CallService::class.java)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
        "showNotification" -> {
          val message: HashMap<String, Any>? = call.arguments()
            if(message != null) {
                checkNotificationStatusAndStartForgroundService(message)
                result.success(true)
            }
        }
        "cancelCallNotification" -> {
          cancelCallNotification()
          result.success(true)
        } "showUnhandledPressedAction" -> {
            showUnhandledPressedAction()
        result.success(true);
        } "isNotificationEnabled" -> {
          result.success(isCallServiceRunning())
        } else -> {
          result.notImplemented()
        }
    }
  }

    private fun showUnhandledPressedAction(){
        if(unHandledActionIntent != null){
            receiveNotificationAction(unHandledActionIntent!!);
            unHandledActionIntent = null;
        }
    }

  private fun receiveNotificationAction(intent: Intent): Boolean {
      if (!intent.getBooleanExtra("isVisited", false)) {
          if (initialActivity != null) {
              initialActivity?.intent = intent
              intent.putExtra("isVisited", true)
          }
          return invokeNotificationReceived(intent)
      }
      return true;
  }

    private fun invokeNotificationReceived(intent: Intent): Boolean {
        if(intent.action == NotificationButtonActions.launchAction ||
                intent.action == NotificationButtonActions.rejectAction ||
                intent.action == NotificationButtonActions.acceptAction){
          val actionModel: ReceivedNotificationData? = NotificationBuilder.buildNotificationActionFromIntent(applicationContext, intent)

          if (actionModel != null) {
              val returnObject: Map<String, Any> = actionModel.toMap()
          if(intent.action == NotificationButtonActions.rejectAction || intent.action == NotificationButtonActions.acceptAction){
                cancelCallNotification()
          }
          channel.invokeMethod(CallNotificationChannel.ReceivedAction, returnObject)
          }
        }
        return true
    }

    private fun isCallServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (service.service.className.contains(".CallService")) {
                return true
            }
        }
        return false
    }

    private fun checkNotificationStatusAndStartForgroundService(notificationData : HashMap<String,Any>){
        if(!isCallServiceRunning()) {
            startCallForegroundService(notificationData)
        }
    }

    private fun startCallForegroundService(notificationData : HashMap<String,Any>){
        foregroundIntent.putExtra("notificationData",notificationData);

        ContextCompat.startForegroundService(applicationContext,foregroundIntent);
        val formatedNotificationData = NotificationData(notificationData)
        notificationTimer = Timer("PersonalNotification",false)
        notificationTimer!!.schedule(timerTask{
            cancelCallNotification()
        },(formatedNotificationData.notificationDuration * 1000).toLong())
    }

  private fun cancelCallNotification(){
      if(isCallServiceRunning()){
          applicationContext.stopService(foregroundIntent)
      }
      notificationTimer?.cancel();
      notificationTimer = null;
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
    initialActivity = activityPluginBinding.activity
    activityPluginBinding.addOnNewIntentListener(this)
    val application = initialActivity!!.application
    application.registerActivityLifecycleCallbacks(this)

    val intent = initialActivity!!.intent
    if(intent.action == NotificationButtonActions.acceptAction || intent.action == NotificationButtonActions.launchAction || intent.action == NotificationButtonActions.rejectAction){
       unHandledActionIntent = intent;
      }
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {

    }

    override fun onDetachedFromActivity() {

    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {

    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onNewIntent(intent: Intent): Boolean {
        return if(intent == null){
            true;
        }else{
            receiveNotificationAction(intent);
        }
    }
}
