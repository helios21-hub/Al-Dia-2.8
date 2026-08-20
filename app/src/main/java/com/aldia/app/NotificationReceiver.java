package com.aldia.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs=context.getSharedPreferences("al_dia",Context.MODE_PRIVATE);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())){
            if(prefs.getBoolean("notifications_enabled",true))NotificationScheduler.scheduleAll(context);
            return;
        }
        if(!prefs.getBoolean("notifications_enabled",true))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;

        String action=intent.getAction();
        if(NotificationScheduler.ACTION_NOTE_REMINDER.equals(action)){
            showNoteReminder(context,prefs,intent.getStringExtra("note_id"));
            NotificationScheduler.scheduleNoteReminders(context);
            return;
        }

        boolean isDaily=NotificationScheduler.ACTION_DAILY.equals(action);
        boolean isRepeat=NotificationScheduler.ACTION_REPEAT.equals(action);
        boolean isTest=NotificationScheduler.ACTION_TEST_PRODUCTS.equals(action);
        if(!(isDaily||isRepeat||isTest))return;

        try{
            JSONObject data=new JSONObject(prefs.getString("notification_data","{}"));
            JSONArray items=data.optJSONArray("items");LocalDate today=LocalDate.now();
            int overdue=0,upcoming=0,todayWithdraw=0;
            if(items!=null)for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);if(item==null)continue;
                String dateStr=item.optString("date","");if(dateStr.isEmpty())continue;
                int withdrawDays=item.optInt("withdrawDays",0);
                LocalDate withdrawal=LocalDate.parse(dateStr).minusDays(withdrawDays);
                long diff=ChronoUnit.DAYS.between(today,withdrawal);
                if(diff<0)overdue++; else if(diff==0)todayWithdraw++; else if(diff<=7)upcoming++;
            }
            List<String> parts=new ArrayList<>();
            if(overdue>0)parts.add(overdue+(overdue==1?" producto con retiro atrasado":" productos con retiro atrasado"));
            if(todayWithdraw>0)parts.add(todayWithdraw+(todayWithdraw==1?" producto para retirar hoy":" productos para retirar hoy"));
            if(!isRepeat&&upcoming>0)parts.add(upcoming+(upcoming==1?" vencimiento próximo":" vencimientos próximos"));

            if(parts.isEmpty()){
                if(isTest)parts.add("No hay productos pendientes ni vencimientos próximos para probar");
                else{if(isRepeat)NotificationScheduler.cancelRepeat(context);return;}
            }
            String title=isTest?"Al Día · Prueba real de productos":"Al Día";
            notify(context,4301,title,String.join(" · ",parts));

            int repeat=prefs.getInt("notification_repeat_minutes",0);
            boolean pending=overdue>0||todayWithdraw>0;
            if((isDaily||isRepeat)&&pending&&(repeat==30||repeat==60||repeat==120))NotificationScheduler.scheduleNextRepeat(context,repeat);
            else if(isRepeat||!pending)NotificationScheduler.cancelRepeat(context);
        }catch(Exception ignored){}
    }

    private void showNoteReminder(Context c,SharedPreferences prefs,String id){
        if(id==null||id.isEmpty())return;
        try{
            JSONObject data=new JSONObject(prefs.getString("notification_data","{}"));
            JSONArray notes=data.optJSONArray("notes");if(notes==null)return;
            for(int i=0;i<notes.length();i++){
                JSONObject n=notes.optJSONObject(i);if(n==null||!id.equals(n.optString("id","")))continue;
                String title=n.optString("title","").trim();
                String text=n.optString("text","").trim().replace("\n"," ");
                if(title.isEmpty())title="Recordatorio de nota";
                if(text.isEmpty())text="Tenés una nota programada para hoy.";
                notify(c,6000+Math.abs(id.hashCode()%1000000),"Al Día · "+title,text);
                break;
            }
        }catch(Exception ignored){}
    }

    private void notify(Context c,int id,String title,String message){
        Intent open=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent content=open==null?null:PendingIntent.getActivity(c,4201,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(c,MainActivity.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true);
        if(content!=null)b.setContentIntent(content);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,b.build());
    }
}
