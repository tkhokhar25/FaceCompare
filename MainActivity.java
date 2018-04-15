package com.blogspot.mobiletechanalyst.facecompare;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private ImageView imageView, imageView2;

    private UUID faceOne, faceTwo;

    private TextView textView;

    boolean secondImageSelected = false;

    private Button selectFirstImage, verifyFaces, selectSecondImage;

    private FaceServiceClient faceServiceClient = new FaceServiceRestClient(ApiConstants.SERVICE_HOST, ApiConstants.SUBSCIPTION_KEY);

    private final int PICK_IMAGE = 1;
    private ProgressDialog detectionProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectFirstImage = (Button)findViewById(R.id.button1);
        textView = (TextView)findViewById(R.id.textView);
        verifyFaces = (Button)findViewById(R.id.button2);
        selectSecondImage = (Button)findViewById(R.id.button0);

        verifyFaces.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verify();
            }
        });


        selectFirstImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secondImageSelected = false;
                Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
                gallIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE);
            }
        });

        selectSecondImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                secondImageSelected = true;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
            }
        });
        detectionProgressDialog = new ProgressDialog(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                imageView = (ImageView) findViewById(R.id.imageView1);
                imageView2 = (ImageView) findViewById(R.id.imageView2);

                if (!isOnline()) {
                    Toast.makeText(getApplicationContext(), "Check your internet connection and try again!", Toast.LENGTH_LONG).show();
                    return;
                }
                
                if (secondImageSelected) {
                    imageView2.setImageBitmap(bitmap);
                    detectAndFrame(bitmap);
                } else {
                    imageView.setImageBitmap(bitmap);
                    detectAndFrame(bitmap);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void detectAndFrame(final Bitmap imageBitmap)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        AsyncTask<InputStream, String, Face[]> detectTask = new AsyncTask<InputStream, String, Face[]>() {
                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting Face");
                            Face[] result = faceServiceClient.detect(params[0], true, false, null);
                            if (result == null)
                            {
                                publishProgress("No faces found in the image");
                                return null;
                            }

                            return result;
                        } catch (Exception e) {
                            publishProgress("Detection failed");
                            return null;
                        }
                    }
                    @Override
                    protected void onPreExecute() {
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {
                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    @Override
                    protected void onPostExecute(Face[] result) {
                        detectionProgressDialog.dismiss();

                        if (result.length == 0) {
                            if (secondImageSelected) {
                                imageView2.setImageDrawable(null);
                            } else {
                                imageView.setImageDrawable(null);
                            }
                            Toast.makeText(getApplicationContext(), "No faces detected in the image", Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (secondImageSelected) {
                            faceTwo = result[0].faceId;
                        } else {
                            faceOne = result[0].faceId;
                        }
                    }
                };
        detectTask.execute(inputStream);
    }

    public void verify() {
        if (!isOnline()) {
            Toast.makeText(getApplicationContext(), "Check your internet connection and try again!", Toast.LENGTH_LONG).show();
            return;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (faceOne != null && faceTwo != null) {
                    try {
                        VerifyResult verifyResult = faceServiceClient.verify(faceOne, faceTwo);
                        double confidence= Math.round((verifyResult.confidence) * 100.0) / 100.0;
                        textView.setText("Confidence that the faces match = " + String.valueOf(confidence) + "%");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please select two valid images", Toast.LENGTH_SHORT).show();
                }
            }
        });
        thread.start();
    }

    public boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();

        return netInfo != null && netInfo.isConnectedOrConnecting();
    }
}


