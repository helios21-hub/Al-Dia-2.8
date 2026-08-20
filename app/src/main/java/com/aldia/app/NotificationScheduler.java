package com.aldia.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class NotificationScheduler {
    private NotificationScheduler() {}
    public static final String ACTION_DAILY="com.aldia.app.DAILY_CHECK";
    public static final String ACTION_REPEAT="com.aldia.app.REPEAT_CHECK";
    public static final String ACTION_TEST_PRODUCTS="com.aldia.app.TEST_PRODUCTS";
    public static final String ACTION_NOTE_REMINDER="com.aldia.app.NOTE_REMINDER";

    private static PendingIntent pi(Context c,String action,int code){
        Intent i=new Intent(c,NotificationReceiver.class);i.setAction(action);
        return PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent notePi(Context c,String id,int code){
        Intent i=new Intent(c,NotificationReceiver.class);i.setAction(ACTION_NOTE_REMINDER);i.putExtra("note_id",id);
        return PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void scheduleAll(Context c){scheduleDaily(c);scheduleNoteReminders(c);}
    public static void scheduleDaily(Context c){
        SharedPreferences p=c.getSharedPreferences("al_dia",Context.MODE_PRIVATE);
        if(!p.getBoolean("notifications_enabled",true))return;
        scheduleDaily(c,p.getInt("notification_hour",6),p.getInt("notification_minute",0));
    }
    public static void scheduleDaily(Context c,int hour,int minute){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Calendar n=Calendar.getInstance();n.set(Calendar.HOUR_OF_DAY,Math.max(0,Math.min(23,hour)));
        n.set(Calendar.MINUTE,Math.max(0,Math.min(59,minute)));n.set(Calendar.SECOND,0);n.set(Calendar.MILLISECOND,0);
        if(n.getTimeInMillis()<=System.currentTimeMillis())n.add(Calendar.DAY_OF_YEAR,1);
        PendingIntent p=pi(c,ACTION_DAILY,4101);a.cancel(p);
        a.setInexactRepeating(AlarmManager.RTC_WAKEUP,n.getTimeInMillis(),AlarmManager.INTERVAL_DAY,p);
    }
    public static void scheduleNextRepeat(Context c,int mins){
        if(mins!=30&&mins!=60&&mins!=120)return;
        Calendar now=Calendar.getInstance(),next=(Calendar)now.clone();next.add(Calendar.MINUTE,mins);
        if(next.get(Calendar.DAY_OF_YEAR)!=now.get(Calendar.DAY_OF_YEAR)||next.get(Calendar.YEAR)!=now.get(Calendar.YEAR)){cancelRepeat(c);return;}
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent p=pi(c,ACTION_REPEAT,4102);a.cancel(p);
        a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),p);
    }
    public static void cancelRepeat(Context c){
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c,ACTION_REPEAT,4102));
    }
    public static void triggerProductTest(Context c){
        c.sendBroadcast(new Intent(c,NotificationReceiver.class).setAction(ACTION_TEST_PRODUCTS));
    }
    public static void scheduleNoteReminders(Context c){
        SharedPreferences prefs=c.getSharedPreferences("al_dia",Context.MODE_PRIVATE);
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Set<String> old=prefs.getStringSet("note_alarm_ids",new HashSet<>());
        for(String encoded:old){
            String[] parts=encoded.split(":",2);
            if(parts.length==2)try{a.cancel(notePi(c,parts[1],Integer.parseInt(parts[0])));}catch(Exception ignored){}
        }
        Set<String> nextIds=new HashSet<>();
        try{
            JSONObject data=new JSONObject(prefs.getString("notification_data","{}"));
            JSONArray notes=data.optJSONArray("notes");if(notes==null)return;
            long now=System.currentTimeMillis();
            for(int i=0;i<notes.length();i++){
                JSONObject n=notes.optJSONObject(i);if(n==null)continue;
                String id=n.optString("id",""),date=n.optString("reminderDate",""),time=n.optString("reminderTime","09:00");
                if(id.isEmpty()||date.isEmpty())continue;
                LocalDate d=LocalDate.parse(date);LocalTime t=LocalTime.parse(time);
                long when=LocalDateTime.of(d,t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if(when<=now)continue;
                int code=5000+Math.abs(id.hashCode()%2000000000);
                PendingIntent p=notePi(c,id,code);a.cancel(p);a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);
                nextIds.add(code+":"+id);
            }
        }catch(Exception ignored){}
        prefs.edit().putStringSet("note_alarm_ids",nextIds).apply();
    }
    public static void cancelAll(Context c){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        a.cancel(pi(c,ACTION_DAILY,4101));a.cancel(pi(c,ACTION_REPEAT,4102));
        SharedPreferences prefs=c.getSharedPreferences("al_dia",Context.MODE_PRIVATE);
        for(String encoded:prefs.getStringSet("note_alarm_ids",new HashSet<>())){
            String[] p=encoded.split(":",2);if(p.length==2)try{a.cancel(notePi(c,p[1],Integer.parseInt(p[0])));}catch(Exception ignored){}
        }
        prefs.edit().remove("note_alarm_ids").apply();
    }
}
