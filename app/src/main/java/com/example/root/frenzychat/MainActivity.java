package com.example.root.frenzychat;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton,logbutton;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    public static final int RC_SIGN_IN=1;
    private static final int RC_PHOTO_PICKER=2;
    private String mUsername;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStoragerefeence;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsername = ANONYMOUS;
        mFirebaseDatabase=FirebaseDatabase.getInstance();
        mFirebaseAuth= FirebaseAuth.getInstance();
        mFirebaseStorage=FirebaseStorage.getInstance();
        mMessagesDatabaseReference=mFirebaseDatabase.getReference().child("messages");
        // Initialize references to views
        mChatPhotosStoragerefeence=mFirebaseStorage.getReference().child("chat_photos");
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        logbutton= (Button) findViewById(R.id.login);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mMessageEditText.setVisibility(View.INVISIBLE);
        mPhotoPickerButton.setVisibility(View.INVISIBLE);
        mSendButton.setVisibility(View.INVISIBLE);

        mSendButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){


                mMessageEditText.setText("");
            }
        });

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
                startActivityForResult(Intent.createChooser(intent,"Complete Action Using "),RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlymessage =new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                mMessagesDatabaseReference.push().setValue(friendlymessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener=new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=mFirebaseAuth.getCurrentUser();
                if(user!=null){
                    //Toast.makeText(MainActivity.this,"You signed In! wow",Toast.LENGTH_LONG).show();
                    onSignedInInitialize(user.getDisplayName());
                }
                else{
                    onSignedOutCleanup();
                    startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().
                            setIsSmartLockEnabled(false).setProviders(AuthUI.GOOGLE_PROVIDER)
                            .build(),RC_SIGN_IN);
                }
            }
        };



    }
    private void onSignedInInitialize(String username){
        mUsername=username;
        attachDatabaseReadListener();

    }
    private void onSignedOutCleanup(){
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();

    }
    private void detachDatabaseReadListener(){
        if(mChildEventListener!=null){
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener=null;
        }
    }
    private void attachDatabaseReadListener(){
        if (mChildEventListener == null) {

            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };

            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(MainActivity.this, "You signed in Successfully", Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(MainActivity.this, "You couldn't sign in", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        else if(requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK){
            Uri selectedImageUri=data.getData();
            Log.d("MainActivity","Wanted to check if this is fired");
            StorageReference photoRef=mChatPhotosStoragerefeence.child(selectedImageUri.getLastPathSegment());
            mProgressBar.setVisibility(View.VISIBLE);
            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    mProgressBar.setVisibility(View.GONE);
                    Uri downloadUrl=  taskSnapshot.getDownloadUrl();
                    FriendlyMessage friendlyMessage=new FriendlyMessage(null,mUsername,downloadUrl.toString());
                    mMessagesDatabaseReference.push().setValue(friendlyMessage);
                }
            });

        }

    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }


    }
    @Override
    protected void onPause() {
        super.onPause();
        if(mAuthStateListener!=null &&isNetworkAvailable()){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(isNetworkAvailable()) {
            mFirebaseAuth.addAuthStateListener(mAuthStateListener);
            mMessageEditText.setVisibility(View.VISIBLE);
            mSendButton.setVisibility(View.VISIBLE);
            mPhotoPickerButton.setVisibility(View.VISIBLE);
            logbutton.setVisibility(View.GONE);

        }
        else
            Toast.makeText(MainActivity.this,"No Connection Available",Toast.LENGTH_LONG).show();
    }
    public void startprocess(View view){
        onResume();
    }
}

