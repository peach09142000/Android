/*
 * Copyright 2004 - Present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.android;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.facebook.android.SessionEvents.AuthListener;
import com.facebook.android.SessionEvents.LogoutListener;

public class Hackbook extends Activity implements OnItemClickListener,SurfaceHolder.Callback{

    /*
     * Your Facebook Application ID must be set before running this example See
     * http://www.facebook.com/developers/createapp.php
     */
    public static final String APP_ID = "175729095772478";

    private LoginButton mLoginButton;
    private TextView mText;
    private ImageView mUserPic;
    private Handler mHandler;    
    ProgressDialog dialog;
    
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    boolean previewing = true;

    final static int AUTHORIZE_ACTIVITY_RESULT_CODE = 0;
    final static int PICK_EXISTING_PHOTO_RESULT_CODE = 1;

    private String graph_or_fql;

    private ListView list;
    String[] main_items = { "Upload Photo" };
    String[] permissions = { "offline_access", "publish_stream", "user_photos", "publish_checkins",
            "photo_upload" };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (APP_ID == null) {
            Util.showAlert(this, "Warning", "Facebook Applicaton ID must be "
                    + "specified before running this example: see FbAPIs.java");
            return;
        }

        setContentView(R.layout.main);
        mHandler = new Handler();

        mText = (TextView) Hackbook.this.findViewById(R.id.txt);
        mUserPic = (ImageView) Hackbook.this.findViewById(R.id.user_pic);

        // Create the Facebook Object using the app id.
        Utility.mFacebook = new Facebook(APP_ID);
        // Instantiate the asynrunner object for asynchronous api calls.
        Utility.mAsyncRunner = new AsyncFacebookRunner(Utility.mFacebook);

        mLoginButton = (LoginButton) findViewById(R.id.login);

        // restore session if one exists
        SessionStore.restore(Utility.mFacebook, this);
        SessionEvents.addAuthListener(new FbAPIsAuthListener());
        SessionEvents.addLogoutListener(new FbAPIsLogoutListener());

        /*
         * Source Tag: login_tag
         */
        mLoginButton.init(this, AUTHORIZE_ACTIVITY_RESULT_CODE, Utility.mFacebook, permissions);

        if (Utility.mFacebook.isSessionValid()) {
            requestUserData();
        }

        list = (ListView) findViewById(R.id.main_list);

        list.setOnItemClickListener(this);
        list.setAdapter(new ArrayAdapter<String>(this, R.layout.main_list_item, main_items));
        
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        if(!previewing){     
        	mCamera = Camera.open();     
        	if (mCamera != null){      
        		try {       
        			mCamera.setPreviewDisplay(mSurfaceHolder);       
        			mCamera.startPreview();       
        			previewing = true;      
        			} catch (IOException e) 
        			{       // TODO Auto-generated catch block       
        				e.printStackTrace();      
        			}    
        		}
        	}
        
        
        
    }

    @Override
    public void onResume() {
        super.onResume();
        if(Utility.mFacebook != null) {
            if (!Utility.mFacebook.isSessionValid()) {
                mText.setText("You are logged out! ");
                mUserPic.setImageBitmap(null);
            } else {
                Utility.mFacebook.extendAccessTokenIfNeeded(this, null);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        /*
         * if this is the activity result from authorization flow, do a call
         * back to authorizeCallback Source Tag: login_tag
         */
            case AUTHORIZE_ACTIVITY_RESULT_CODE: {
                Utility.mFacebook.authorizeCallback(requestCode, resultCode, data);
                break;
            }
            /*
             * if this is the result for a photo picker from the gallery, upload
             * the image after scaling it. You can use the Utility.scaleImage()
             * function for scaling
             */
            case PICK_EXISTING_PHOTO_RESULT_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    Uri photoUri = data.getData();
                    if (photoUri != null) {
                        Bundle params = new Bundle();
                        try {
                            params.putByteArray("photo",
                                    Utility.scaleImage(getApplicationContext(), photoUri));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        params.putString("caption", "140,112");
                        Utility.mAsyncRunner.request("me/photos", params, "POST",
                                new PhotoUploadListener(), null);
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Error selecting image from the gallery.", Toast.LENGTH_SHORT)
                                .show();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "No image selected for upload.",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
        switch (position) {
        /*
         * Source Tag: update_status_tag Update user's status by invoking the
         * feed dialog To post to a friend's wall, provide his uid in the 'to'
         * parameter Refer to
         * https://developers.facebook.com/docs/reference/dialogs/feed/ for more
         * info.
         */
           
            /*
             * Source Tag: upload_photo You can upload a photo from the media
             * gallery or from a remote server How to upload photo:
             * https://developers.facebook.com/blog/post/498/
             */
            case 0: {
                if (!Utility.mFacebook.isSessionValid()) {
                    Util.showAlert(this, "Warning", "You must first log in.");
                } else {
                    dialog = ProgressDialog.show(Hackbook.this, "",
                            getString(R.string.please_wait), true, true);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.gallery_remote_title)
                            .setMessage(R.string.gallery_remote_msg)
                            .setPositiveButton(R.string.gallery_button,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(Intent.ACTION_PICK,
                                                    (MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                                            startActivityForResult(intent,
                                                    PICK_EXISTING_PHOTO_RESULT_CODE);
                                        }

                                    })
                            .setNegativeButton(R.string.remote_button,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            /*
                                             * Source tag: upload_photo_tag
                                             */
                                            Bundle params = new Bundle();
                                            params.putString("url",
                                                    "http://www.facebook.com/images/devsite/iphone_connect_btn.jpg");
                                            params.putString("caption",
                                                    "FbAPIs Sample App photo upload");
                                            Utility.mAsyncRunner.request("me/photos", params,
                                                    "POST", new PhotoUploadListener(), null);
                                        }

                                    })
                           .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface d) {
                                    dialog.dismiss();
                                }
                            }).show();
                }
                break;
            }

        }
    }

    /*
     * callback for the feed dialog which updates the profile status
     */
    public class UpdateStatusListener extends BaseDialogListener {
        @Override
        public void onComplete(Bundle values) {
            final String postId = values.getString("post_id");
            if (postId != null) {
                new UpdateStatusResultDialog(Hackbook.this, "Update Status executed", values)
                        .show();
            } else {
                Toast toast = Toast.makeText(getApplicationContext(), "No wall post made",
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }

        @Override
        public void onFacebookError(FacebookError error) {
            Toast.makeText(getApplicationContext(), "Facebook Error: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
            Toast toast = Toast.makeText(getApplicationContext(), "Update status cancelled",
                    Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    
    /*
     * callback for the photo upload
     */
    public class PhotoUploadListener extends BaseRequestListener {

        @Override
        public void onComplete(final String response, final Object state) {
            dialog.dismiss();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    new UploadPhotoResultDialog(Hackbook.this, "Upload Photo executed", response)
                            .show();
                }
            });
        }

        public void onFacebookError(FacebookError error) {
            dialog.dismiss();
            Toast.makeText(getApplicationContext(), "Facebook Error: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    
    /*
     * Callback for fetching current user's name, picture, uid.
     */
    public class UserRequestListener extends BaseRequestListener {

        @Override
        public void onComplete(final String response, final Object state) {
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(response);

                final String picURL = jsonObject.getString("picture");
                final String name = jsonObject.getString("name");
                Utility.userUID = jsonObject.getString("id");

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mText.setText("Welcome " + name + "!");
                        mUserPic.setImageBitmap(Utility.getBitmap(picURL));
                    }
                });

            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    /*
     * The Callback for notifying the application when authorization succeeds or
     * fails.
     */

    public class FbAPIsAuthListener implements AuthListener {

        @Override
        public void onAuthSucceed() {
            requestUserData();
        }

        @Override
        public void onAuthFail(String error) {
            mText.setText("Login Failed: " + error);
        }
    }

    /*
     * The Callback for notifying the application when log out starts and
     * finishes.
     */
    public class FbAPIsLogoutListener implements LogoutListener {
        @Override
        public void onLogoutBegin() {
            mText.setText("Logging out...");
        }

        @Override
        public void onLogoutFinish() {
            mText.setText("You have logged out! ");
            mUserPic.setImageBitmap(null);
        }
    }

    /*
     * Request user name, and picture to show on the main screen.
     */
    public void requestUserData() {
        mText.setText("Fetching user name, profile pic...");
        Bundle params = new Bundle();
        params.putString("fields", "name, picture");
        Utility.mAsyncRunner.request("me", params, new UserRequestListener());
    }

    /**
     * Definition of the list adapter
     */
    public class MainListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public MainListAdapter() {
            mInflater = LayoutInflater.from(Hackbook.this.getBaseContext());
        }

        @Override
        public int getCount() {
            return main_items.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View hView = convertView;
            if (convertView == null) {
                hView = mInflater.inflate(R.layout.main_list_item, null);
                ViewHolder holder = new ViewHolder();
                holder.main_list_item = (TextView) hView.findViewById(R.id.main_api_item);
                hView.setTag(holder);
            }

            ViewHolder holder = (ViewHolder) hView.getTag();

            holder.main_list_item.setText(main_items[position]);

            return hView;
        }

    }

    class ViewHolder {
        TextView main_list_item;
    }

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}

}
