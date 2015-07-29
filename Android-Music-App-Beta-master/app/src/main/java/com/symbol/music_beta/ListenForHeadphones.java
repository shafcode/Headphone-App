package com.symbol.music_beta;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class ListenForHeadphones extends Service {

    private boolean isRunning = false;
    private MediaPlayer mp = new MediaPlayer();
    private int elapsedTime = 600001;
    private int length = 0;
    private String musicState = "";
    private ArrayList<String> songPaths;
    private int mode = 0;
    private String notificationContent = "";
    private String notificationTitle = "";
    private String speakerStatus = "";

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
        try{
            StaticMethods.write("musicState.txt", "play", getBaseContext());//initialize musicState to play
        }catch(IOException e){}
        songPaths = StaticMethods.getSongPath(getBaseContext());
        for(String s: songPaths){
            System.out.println(s);
        }
        return START_STICKY;
    }
    @Override
    public void onCreate() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(receiver, filter);
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter1);
        registerReceiver(mReceiver, filter2);
        registerReceiver(mReceiver, filter3);
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        ComponentName eventReceiver = new ComponentName(getPackageName(), HeadphoneButtonListener.class.getName());
        am.registerMediaButtonEventReceiver(eventReceiver);//register media button
        try{
            speakerStatus = StaticMethods.readFirstLine("speaker-status",this);
        }catch(IOException e){}
        if(speakerStatus.equals("yes")){
            //startMusic();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch(state){
                    case 1:
                        startMusic();
                        break;
                    case 0:
                        stopMusic();
                        break;
                }
            }
        }
    };

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                startMusic();
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                stopMusic();
            }
        }
    };

    class StartMusicTask extends AsyncTask<Void,Void,Void>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void ...params) {
            if(elapsedTime >= 600000){ //10 minutes
                mp.stop();
                mp.release();
                mp = new MediaPlayer();
                playSong();
            }else{
                mp.seekTo(length);
                mp.start();
            }
            elapsedTime = 0;

            //keep playing songs until headphones are unplugged
            while(true){
                length = mp.getCurrentPosition();
                if(!isRunning){
                    mp.pause();
                    length = mp.getCurrentPosition();
                    break;
                }
                try{
                    musicState = StaticMethods.readFirstLine("musicState.txt",getBaseContext());
                }catch(IOException e){}
                if(musicState == null){
                    musicState = "";
                }
                if(musicState.equals("pause") && mp.isPlaying()){
                    mp.pause();
                }
                if(musicState.equals("skip song")){
                    onComplete();
                }
                if(musicState.equals("play") && !mp.isPlaying() && (mp.getDuration() <= length + 500)) {//song finished (within 500 milliseconds)
                    onComplete();
                }
                if (musicState.equals("play") && !mp.isPlaying() && (mp.getDuration() > length)) {//resume from pause
                    mp.seekTo(length);
                    mp.start();
                }
                try{
                    mode = Integer.parseInt(StaticMethods.readFirstLine("options.txt",getBaseContext()));
                }catch(IOException e){}
                if(mode == 2){
                    Toast.makeText(getBaseContext(), "AutoBeats has been disabled", Toast.LENGTH_LONG).show();
                    Toast.makeText(getBaseContext(), "Change settings and replug to enable", Toast.LENGTH_LONG).show();
                    isRunning = false;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }

    }
    class StopMusicTask extends AsyncTask<Void,Void,Void>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void ...params) {
            elapsedTime += 1;//elapsed time is only 0 when set in StartMusicTask
            while(elapsedTime <= 600000){
                if(elapsedTime == 0 || isRunning){
                    break;
                }
                int currentMillisecond = StaticMethods.getMillisecond();
                try{Thread.sleep(500);}catch(InterruptedException e){}
                int previousMillisecond = currentMillisecond;
                currentMillisecond = StaticMethods.getMillisecond();
                elapsedTime += StaticMethods.calculateDifferenceInMilliseconds(previousMillisecond, currentMillisecond);
                System.out.println("ELAPSED TIME: " + elapsedTime);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }
    }

    private void playSong(){
        int song = 0;
        try{
            mode = Integer.parseInt(StaticMethods.readFirstLine("options.txt",getBaseContext()));
        }catch(IOException e){}
        try{
            if(mode == 0){
                Random rand = new Random();
                song = rand.nextInt(songPaths.size());//skip preloaded crap
                notificationContent = StaticMethods.getTitleFromUriString(songPaths.get(song));
                notificationTitle = StaticMethods.getArtistFromUriString(songPaths.get(song));
                mp.setDataSource(songPaths.get(song));
                mp.prepare();
                mp.start();
            }
            if(mode == 1){
                //add condition to check if movement is true or not
                ArrayList<String> playListSongs = new ArrayList<String>();
                String playlistChoice = StaticMethods.readFirstLine("playlist-choice.txt",getBaseContext());
                String file = "playlist"+playlistChoice+".txt";
                playListSongs = StaticMethods.readFile(file, getBaseContext());//only stationary for now
                Random rand = new Random();
                song = rand.nextInt(playListSongs.size());
                notificationContent = StaticMethods.getTitleFromUriString(playListSongs.get(song));
                notificationTitle = StaticMethods.getArtistFromUriString(playListSongs.get(song));
                mp.setDataSource(playListSongs.get(song));
                mp.prepare();
                mp.start();
            }
        }catch(IOException e){
        }
        makeNotification(notificationTitle,notificationContent);
    }
    private void onComplete(){
        mp.stop();
        mp.release();
        mp = new MediaPlayer();
        playSong();
        try{
            StaticMethods.write("musicState.txt", "play", getBaseContext());//used for skip function
        }catch(IOException e){}
    }

    private void makeNotification(String artist, String title){

        int notificationID = 1;
        Intent intent1 = new Intent(getBaseContext(),NotificationPausePlayReceiver.class);
        PendingIntent playPauseIntent = PendingIntent.getBroadcast(getBaseContext(), 0, intent1, PendingIntent.FLAG_CANCEL_CURRENT);

        Intent intent2 = new Intent(getBaseContext(),NotificationSkipReceiver.class);
        PendingIntent skipIntent = PendingIntent.getBroadcast(getBaseContext(), 0, intent2, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true);
        builder.setContentTitle(artist);
        builder.setContentText(title);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setOngoing(true);

        builder.addAction(R.mipmap.ic_launcher, "play/pause", playPauseIntent);
        builder.addAction(R.mipmap.ic_launcher, "skip", skipIntent);

        Notification n  = builder.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(notificationID, n);
    }

    private void startMusic(){
        try{
            StaticMethods.write("musicState.txt", "play", getBaseContext());//always plays when headphones are plugged in
        }catch(IOException e){}
        try{
            mode = Integer.parseInt(StaticMethods.readFirstLine("options.txt",getBaseContext()));
        }catch(IOException e){}
        if(mode != 2){
            isRunning = true;
            StartMusicTask m = new StartMusicTask();
            m.execute();
        }
    }

    private void stopMusic(){
        isRunning = false;
        StopMusicTask sm = new StopMusicTask();
        sm.execute();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

}
