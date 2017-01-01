package devfigas.com.requestobservable;


import android.graphics.drawable.Drawable;
import android.support.v7.widget.ViewUtils;
import android.view.View;

import retrofit2.Call;
import retrofit2.Response;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

public class RequestObservable<ResponseType> {

    private  Call<ResponseType> mRequest;
    private SnackBar mSnackBarRetry;
    private Observable<ResponseType> mObservable;
    private Subscription mSubscription;
    private SubscriptionCallback mSubscriptionCallback;
    private SubscriptionCallbackSwitch mSubscriptionCallbackSwitch;
    private boolean complete = false;

    public RequestObservable(final SubscriptionCallback callback){
        this.mSubscriptionCallback = callback;
    }
    public RequestObservable(final SubscriptionCallbackSwitch callback){
        this.mSubscriptionCallbackSwitch = callback;
    }

    public void enableRetry(View view){
        String message = view.getContext().getString(R.string.error_connection);
        String action = view.getContext().getString(R.string.actions_retry);
        Drawable icon = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            icon = view.getContext().getResources().getDrawable(R.drawable.ic_replay_white_48dp,view.getContext().getTheme());
        }else{
            icon = view.getContext().getResources().getDrawable(R.drawable.ic_replay_white_48dp);
        }
        enableRetry(view,message,action,icon);
    }
    public void enableRetry(View view,String message, String action,Drawable icon){
       mSnackBarRetry = new SnackBar()
               .view(view)
               .backgroundColor(R.color.red)
               .duration(SnackBar.SnackBarDuration.LONG)
               .setIconForAction(icon, SnackBar.IconPosition.RIGHT, 0)
               .text(message,action)
               .setOnClickListener(true, new SnackBar.OnActionClickListener() {
                   @Override
                   public void onClick(View view) {
                       mSubscription = mObservable.subscribe();
                   }
               });
    }
    public void subscribe(final int requestCode, final RequestCallback<ResponseType> callback,boolean override){
        if(override){
            unsubscribe();
            proccessSubscribe(requestCode,callback);
        }else{
            if(isUnsuscribed()){
                proccessSubscribe(requestCode,callback);
            }
        }
    }

    public void subscribe(final int requestCode, final RequestCallback<ResponseType> callback){
        subscribe(requestCode, callback, false);
    }
    public void subscribe(final RequestCallback<ResponseType> callback){
        subscribe(0,callback, false);
    }

    public void proccessSubscribe(final int requestCode,  final RequestCallback<ResponseType> callback ){
        complete = true;
        beforeRequest(requestCode);
        mObservable = Observable.create(new Observable.OnSubscribe<ResponseType>() {
            @Override
            public void call(final Subscriber<? super ResponseType> subscriber) {
                mRequest = callback.buildRequest();
                mRequest.enqueue(new retrofit2.Callback<ResponseType>() {
                    @Override
                    public void onResponse(Call<ResponseType> call, Response<ResponseType> response) {
                        callback.onResponse(call,response,!subscriber.isUnsubscribed());
                        subscriber.onCompleted();
                        mRequest = null;
                    }

                    @Override
                    public void onFailure(Call<ResponseType> call, Throwable t) {
                        callback.onFailure(call,t,!subscriber.isUnsubscribed());
                        subscriber.onCompleted();
                        if(!subscriber.isUnsubscribed() && mSnackBarRetry !=null){
                            mSnackBarRetry.show();
                        }
                        mRequest = null;
                    }
                });
            }
        })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if(mRequest!=null) mRequest.cancel();
                        afterRequest(requestCode);
                    }
                })
                .subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread());

        mSubscription = mObservable.subscribe();
    }
    public void proccessSubscribe(final RequestCallback<ResponseType> callback ){
        proccessSubscribe(0,callback);
    }
    private void beforeRequest(int requestCode){
        if(mSubscriptionCallback!=null) mSubscriptionCallback.onBeforeRequest();
        else if(mSubscriptionCallbackSwitch!=null) mSubscriptionCallbackSwitch.onBeforeRequest(requestCode);
    }
    private void afterRequest(int requestCode){
        if(mSubscriptionCallback!=null) mSubscriptionCallback.onAfterRequest(complete);
        else if(mSubscriptionCallbackSwitch!=null) mSubscriptionCallbackSwitch.onAfterRequest(requestCode, complete);
    }

    public void unsubscribe(){
        if(!isUnsuscribed()){
            complete = false;
            mSubscription.unsubscribe();
        }
    }
    public boolean isUnsuscribed(){
       return (mSubscription==null || mSubscription.isUnsubscribed());
    }
    public boolean isSubscribed(){
        return !isUnsuscribed();
    }

    public interface SubscriptionCallback{
       public void onBeforeRequest();
       public void onAfterRequest(boolean complete);
   }


    public interface SubscriptionCallbackSwitch{
        public void onBeforeRequest(int code);
        public void onAfterRequest(int code, boolean complete);
    }

    public interface RequestCallback<ResponseType>{
        public Call<ResponseType> buildRequest();
        public void onResponse(Call<ResponseType> call, Response<ResponseType> response, boolean subscribed);
        public void onFailure(Call<ResponseType> call, Throwable t, boolean subscribed);
    }
}
