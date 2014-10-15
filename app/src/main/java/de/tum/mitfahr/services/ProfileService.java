package de.tum.mitfahr.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.net.URL;
import java.util.Date;

import de.tum.mitfahr.BusProvider;
import de.tum.mitfahr.TUMitfahrApplication;
import de.tum.mitfahr.events.GetUserEvent;
import de.tum.mitfahr.events.LoginEvent;
import de.tum.mitfahr.events.RegisterEvent;
import de.tum.mitfahr.events.UpdateUserEvent;
import de.tum.mitfahr.networking.clients.ProfileRESTClient;
import de.tum.mitfahr.networking.models.User;
import de.tum.mitfahr.networking.models.requests.UpdateUserRequest;
import de.tum.mitfahr.networking.models.response.GetUserResponse;
import de.tum.mitfahr.networking.models.response.LoginResponse;
import de.tum.mitfahr.networking.models.response.RegisterResponse;
import de.tum.mitfahr.networking.models.response.UpdateUserResponse;

/**
 * Created by abhijith on 09/05/14.
 */
public class ProfileService {

    private static final String AMZ_BUCKET_NAME = "tumitfahrer";
    private static final String AMZ_SECRET_KEY = "fvyTaWgegKsT1esZo5DrXmc65paKfY5So8jcvQAk";
    private static final String AMZ_ACCESS_KEY_ID = "AKIAIRFGBPO5JUZWRDHA";
    private static final String AMZ_PATH = "users/";
    private static final String AMZ_FILENAME = "/profile_picture.jpg";

    private SharedPreferences mSharedPreferences;
    private ProfileRESTClient mProfileRESTClient;
    private Bus mBus;
    private String userAPIKey;
    private int userId;

    public ProfileService(final Context context) {
        String baseBackendURL = TUMitfahrApplication.getApplication(context).getBaseURLBackend();
        mBus = BusProvider.getInstance();
        mBus.register(this);
        mProfileRESTClient = new ProfileRESTClient(baseBackendURL);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        userId = mSharedPreferences.getInt("id", 0);
        userAPIKey = mSharedPreferences.getString("api_key", null);
    }

    public ProfileService() {

    }

    public void login(String email, String password) {
        mProfileRESTClient.login(email, password);
    }

    public void register(String email, String firstName, String lastName, String department) {
        mProfileRESTClient.registerUserAccount(email, firstName, lastName, department, true);
    }

    public boolean isLoggedIn() {
        // TODO : check login using the shared preferences! :)
        String apiKey = mSharedPreferences.getString("api_key", null);
        return apiKey != null;
    }

    @Subscribe
    public void onLoginResult(LoginEvent result) {
        if (result.getType() == LoginEvent.Type.LOGIN_RESULT) {
            LoginResponse response = result.getResponse();
            if (null == response.getUser()) {
                mBus.post(new LoginEvent(LoginEvent.Type.LOGIN_FAILED));
            } else {
                addUserToSharedPreferences(response.getUser());
                mBus.post(new LoginEvent(LoginEvent.Type.LOGIN_SUCCESSFUL));
            }
        }
    }

    @Subscribe
    public void onRegisterResult(RegisterEvent result) {
        if (result.getType() == RegisterEvent.Type.REGISTER_RESULT) {
            RegisterResponse response = result.getResponse();
            if (null != response.getStatus() && response.getStatus().equals("bad_request")) {
                mBus.post(new RegisterEvent(RegisterEvent.Type.REGISTER_FAILED));
            } else {
                mBus.post(new RegisterEvent(RegisterEvent.Type.REGISTER_SUCCESSFUL));
            }
        }
    }

    public User getUserSynchronous(int userId) {
        return mProfileRESTClient.getUserSynchronous(userId, userAPIKey);
    }

    public void getUser(int userId) {
        mProfileRESTClient.getSomeUser(userId, userAPIKey);
    }

    @Subscribe
    public void onGetUserResult(GetUserEvent result) {
        if (result.getType() == GetUserEvent.Type.RESULT) {
            GetUserResponse response = result.getGetUserResponse();
            if (null == response.getUser()) {
                mBus.post(new GetUserEvent(GetUserEvent.Type.GET_FAILED, response, result.getRetrofitResponse()));
            } else {
                mBus.post(new GetUserEvent(GetUserEvent.Type.GET_SUCCESSFUL, response, result.getRetrofitResponse()));
            }
        }
    }

    public void updateUser(User updatedUser, String email, String password, String passwordConfirmation) {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest(updatedUser, password, passwordConfirmation);
        mProfileRESTClient.updateUser(userId, updateUserRequest, email, password);
    }

    @Subscribe
    public void onUpdateUserResult(UpdateUserEvent result) {
        if (result.getType() == UpdateUserEvent.Type.RESULT) {
            UpdateUserResponse response = result.getUpdateUserResponse();
            if (null == response.getUser()) {
                mBus.post(new UpdateUserEvent(UpdateUserEvent.Type.UPDATE_FAILED, response, result.getRetrofitResponse()));
            } else {
                mBus.post(new UpdateUserEvent(UpdateUserEvent.Type.USER_UPDATED, response, result.getRetrofitResponse()));
            }
        }
    }

    public void forgotPassword(String email) {
        mProfileRESTClient.forgotPassword(email);
    }

    public User getUserFromPreferences() {

        int id = mSharedPreferences.getInt("id", 0);
        String firstName = mSharedPreferences.getString("first_name", "");
        String lastName = mSharedPreferences.getString("last_name", "");
        String email = mSharedPreferences.getString("email", "");
        String phoneNumber = mSharedPreferences.getString("phone_number", "");
        String department = mSharedPreferences.getString("department", "");
        String car = mSharedPreferences.getString("car", "");
        boolean isStudent = mSharedPreferences.getBoolean("is_student", true);
        String apiKey = mSharedPreferences.getString("api_key", "");
        int ratingAverage = mSharedPreferences.getInt("rating_average", 0);
        String createdAt = mSharedPreferences.getString("created_at", "");
        String updatedAt = mSharedPreferences.getString("updated_at", "");

        User currentUser = new User(
                id, firstName, lastName, email,
                phoneNumber, department, car,
                isStudent, apiKey, ratingAverage,
                createdAt, updatedAt);
        return currentUser;
    }

    private void addUserToSharedPreferences(User user) {
        Log.d("USER", user.toString());
        SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
        prefEditor.putInt("id", user.getId());
        prefEditor.putString("first_name", user.getFirstName());
        prefEditor.putString("last_name", user.getLastName());
        prefEditor.putString("email", user.getEmail());
        prefEditor.putString("phone_number", user.getPhoneNumber());
        prefEditor.putString("car", user.getCar());
        prefEditor.putBoolean("is_student", user.isStudent());
        prefEditor.putString("api_key", user.getApiKey());
        prefEditor.putInt("rating_average", user.getRatingAverage());
        prefEditor.putString("created_at", user.getCreatedAt());
        prefEditor.putString("updated_at", user.getUpdatedAt());

        String deptString = user.getDepartment();
        String deptIndex = deptString.replaceAll("[^0-9]", "");
        Log.e("DeptIndex", deptIndex);

        prefEditor.putString("department", deptIndex);
        prefEditor.commit();
    }

    public URL getProfileImageURL() {
        int id = mSharedPreferences.getInt("id", 0);
        String userId = Integer.toString(id);
        ResponseHeaderOverrides override = new ResponseHeaderOverrides();
        override.setContentType("image/jpeg");
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(AMZ_BUCKET_NAME, AMZ_PATH + userId + AMZ_FILENAME);
        request.setMethod(HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + (long) 1000 * 3 * 60)); // 3 minutes
        request.setResponseHeaders(override);

        AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(AMZ_ACCESS_KEY_ID, AMZ_SECRET_KEY));
        URL urlForGet = s3Client.generatePresignedUrl(request);
        return urlForGet;
    }

    public URL getProfileImageURL(int userId) {
        int id = mSharedPreferences.getInt("id", 0);
        ResponseHeaderOverrides override = new ResponseHeaderOverrides();
        override.setContentType("image/jpeg");
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(AMZ_BUCKET_NAME, AMZ_PATH + userId + AMZ_FILENAME);
        request.setMethod(HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + (long) 1000 * 3 * 60)); // 3 minutes
        request.setResponseHeaders(override);

        AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(AMZ_ACCESS_KEY_ID, AMZ_SECRET_KEY));
        URL urlForGet = s3Client.generatePresignedUrl(request);
        return urlForGet;
    }

    public boolean uploadImage(String filePath) {
        AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(AMZ_ACCESS_KEY_ID, AMZ_SECRET_KEY));
        PutObjectRequest por = new PutObjectRequest(AMZ_BUCKET_NAME, AMZ_PATH + userId + AMZ_FILENAME, new java.io.File(filePath));
        s3Client.putObject(por);
        return false;
    }

}
