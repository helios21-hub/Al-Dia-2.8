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

    private File resolve(Uri uri) throws FileNotFoundException {
        if(uri==null||uri.getPathSegments().size()<2)throw new FileNotFoundException();
        String area=uri.getPathSegments().get(0);
        String name=uri.getPathSegments().get(1);
        if(!"xlsx".equals(area)||name.contains("/")||name.contains(".."))throw new FileNotFoundException();
        File f=new File(new File(getContext().getFilesDir(),"xlsx"),name);
        if(!f.exists())throw new FileNotFoundException();
        return f;
    }

    @Override public String getType(Uri uri){
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File f=resolve(uri);String display=uri.getQueryParameter("name");if(display==null||display.isEmpty())display=f.getName();
            String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
            MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=display;else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();else row[i]=null;}
            c.addRow(row);return c;
        }catch(Exception e){return null;}
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
}
