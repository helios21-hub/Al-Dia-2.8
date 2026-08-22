package com.aldia.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class AppFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}

    private File resolve(Uri uri, boolean createCapture) throws FileNotFoundException {
        if(uri==null||uri.getPathSegments().size()<2)throw new FileNotFoundException();
        String area=uri.getPathSegments().get(0);
        String name=uri.getPathSegments().get(1);
        if(name.contains("/")||name.contains(".."))throw new FileNotFoundException();
        if("xlsx".equals(area)){
            File f=new File(new File(getContext().getFilesDir(),"xlsx"),name);
            if(!f.exists())throw new FileNotFoundException();
            return f;
        }
        if("capture".equals(area)){
            File dir=new File(getContext().getCacheDir(),"capture");
            if(!dir.exists()&&!dir.mkdirs())throw new FileNotFoundException();
            File f=new File(dir,name);
            if(createCapture&&!f.exists())try{if(!f.createNewFile())throw new FileNotFoundException();}catch(Exception e){throw new FileNotFoundException();}
            return f;
        }
        throw new FileNotFoundException();
    }

    @Override public String getType(Uri uri){
        if(uri!=null&&uri.getPathSegments().size()>0&&"capture".equals(uri.getPathSegments().get(0)))return "image/jpeg";
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File f=resolve(uri,false);String display=uri.getQueryParameter("name");if(display==null||display.isEmpty())display=f.getName();
            String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
            MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=display;else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();else row[i]=null;}
            c.addRow(row);return c;
        }catch(Exception e){return null;}
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        boolean write=mode!=null&&mode.contains("w");
        File f=resolve(uri,write);
        int flags;
        if(write && mode.contains("r")) flags=ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE;
        else if(write) flags=ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY;
        else flags=ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f,flags);
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
}
