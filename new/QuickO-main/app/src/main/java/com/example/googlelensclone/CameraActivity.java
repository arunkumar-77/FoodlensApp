package com.example.googlelensclone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private ImageView captureIV;
    private Button snapBtn, getSearchResultsBtn, captureBtn;
    private RecyclerView resultsRV;

    //Newly added code
    private static final int REQUEST_CAMERA_CODE = 100;

    private SearchRVAdapter searchRVAdapter;
    private ArrayList<SearchRVModal> searchRVModalArrayList;
    int REQUEST_CODE = 1;
    private ProgressBar loadingPB;
    private Bitmap imageBitmap;
    String title, link, displayedLink, snippet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //Newly Added code
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{
                    Manifest.permission.CAMERA
            },REQUEST_CAMERA_CODE);
        }

        captureBtn = findViewById(R.id.idBtnCapture); // capture button for OCR
        captureIV = findViewById(R.id.image);
        snapBtn = findViewById(R.id.idBtnSnap);
        getSearchResultsBtn = findViewById(R.id.idBtnResults);
        resultsRV = findViewById(R.id.idRVSearchResults);
        loadingPB = findViewById(R.id.idPBLoading);
        searchRVModalArrayList = new ArrayList<>();
        searchRVAdapter = new SearchRVAdapter(this, searchRVModalArrayList);
        resultsRV.setLayoutManager(new LinearLayoutManager(CameraActivity.this, LinearLayoutManager.HORIZONTAL,false));
        resultsRV.setAdapter(searchRVAdapter);

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(CameraActivity.this, ScannerActivity.class);
                startActivity(i);
            }
        });

        snapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchRVModalArrayList.clear();
                searchRVAdapter.notifyDataSetChanged();
                takePictureIntent();
            }
        });

        getSearchResultsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchRVModalArrayList.clear();
                searchRVAdapter.notifyDataSetChanged();
                loadingPB.setVisibility(View.VISIBLE);
                getResults();
            }
        });

    }

    private void getResults(){

        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(imageBitmap);
        FirebaseVisionImageLabeler labeler = FirebaseVision.getInstance().getOnDeviceImageLabeler();

        labeler.processImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
            @Override
            public void onSuccess(List<FirebaseVisionImageLabel> firebaseVisionImageLabels) {
                String searchQuery = firebaseVisionImageLabels.get(0).getText();
                getSearchResults(searchQuery);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(CameraActivity.this, "Failed to detect image!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK){
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            captureIV.setImageBitmap(imageBitmap);
        }
    }

    private void getSearchResults(String searchQuery){
        String url = "https://serpapi.com/search.json?engine=google&q="+searchQuery+"&location=Seattle-Tacoma%2C+WA%2C+Washington%2C+United+States&hl=en&gl=us&google_domain=google.com&num=10&start=10&safe=active&api_key=11ea7f017556e72caf4ab7f863394e8b11f28a22b3b18fa3faedc5fe2dbe23c5";
        RequestQueue queue = Volley.newRequestQueue(CameraActivity.this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                loadingPB.setVisibility(View.GONE);
                try {
                    JSONArray organicArray = response.getJSONArray("organic_results");
                    for (int i=0; i<organicArray.length(); i++){
                        JSONObject organicObj = organicArray.getJSONObject(i);
                        if (organicObj.has("title")){
                            title = organicObj.getString("title");
                        }
                        if (organicObj.has("link")){
                            link = organicObj.getString("link");
                        }
                        if (organicObj.has("displayed_link")){
                            displayedLink = organicObj.getString("displayed_link");
                        }
                        if (organicObj.has("thumbnail")){
                            snippet = organicObj.getString("thumbnail");
                        }

                        searchRVModalArrayList.add(new SearchRVModal(title,link,displayedLink,snippet));
                    }

                    searchRVAdapter.notifyDataSetChanged();

                }catch (JSONException e){
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(CameraActivity.this, "No results found!", Toast.LENGTH_SHORT).show();
            }
        });
        queue.add(jsonObjectRequest);
    }

    private void takePictureIntent() {

        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (i.resolveActivity(getPackageManager())!=null){
            startActivityForResult(i,REQUEST_CODE);
        }
    }
}