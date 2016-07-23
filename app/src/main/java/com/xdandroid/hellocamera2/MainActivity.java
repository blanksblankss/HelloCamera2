package com.xdandroid.hellocamera2;

import android.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.support.v7.app.*;
import android.view.*;
import android.widget.*;

import com.facebook.drawee.backends.pipeline.*;
import com.facebook.drawee.view.*;
import com.facebook.imagepipeline.core.*;
import com.gun0912.tedpermission.*;
import com.xdandroid.hellocamera2.app.*;
import com.xdandroid.hellocamera2.util.*;

import java.io.*;
import java.util.*;

import butterknife.*;
import rx.Observable;
import rx.android.schedulers.*;
import rx.functions.*;
import rx.schedulers.*;

public class MainActivity extends BaseActivity {

    @BindView(R.id.iv) SimpleDraweeView iv;
    @BindView(R.id.btn_takepicture) Button btnTakepicture;

    private File mFile;
    private boolean mHasSelectedOnce;

    @OnClick(R.id.iv)
    void onImageViewClick(View v) {
        if (mHasSelectedOnce) {
            View dialogView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog, null, false);
            SimpleDraweeView ivBig = (SimpleDraweeView) dialogView.findViewById(R.id.iv_dialog_big);
            FrescoUtils.load("file://" + mFile.toString()).into(ivBig);
            AlertDialog dialog = new AlertDialog
                    .Builder(MainActivity.this, R.style.Dialog_Translucent)
                    .setView(dialogView).create();
            ivBig.setOnClickListener(v1 -> dialog.dismiss());
            dialog.show();
        } else {
            openContextMenu(btnTakepicture);
        }
    }

    @OnClick(R.id.btn_takepicture)
    void onTakepictureClick(View v) {
        openContextMenu(v);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle("选择要使用的相机");
        menu.add(0, 0, 0, "系统相机");
        menu.add(0, 1, 1, "自定义相机");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == 0) {
            new TedPermission(App.app)
                    .setRationaleMessage("我们需要使用您设备上的相机以完成拍照。\n当 Android 系统请求将相机权限授予 HelloCamera2 时，请选择『允许』。")
                    .setDeniedMessage("如果您不对 HelloCamera2 授予相机权限，您将不能完成拍照。")
                    .setRationaleConfirmText("确定")
                    .setDeniedCloseButtonText("关闭")
                    .setGotoSettingButtonText("设定")
                    .setPermissionListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted() {
                            /*调用系统相机进行拍照*/
                            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            mFile = CommonUtils.createFile("mFile");
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mFile));
                            startActivityForResult(intent, App.TAKE_PHOTO_SYSTEM);
                        }

                        @Override
                        public void onPermissionDenied(ArrayList<String> arrayList) {

                        }
                    }).setPermissions(new String[]{Manifest.permission.CAMERA})
                    .check();
        } else {
            new TedPermission(App.app)
                    .setRationaleMessage("我们需要使用您设备上的相机以完成拍照。\n当 Android 系统请求将相机权限授予 HelloCamera2 时，请选择『允许』。")
                    .setDeniedMessage("如果您不对 HelloCamera2 授予相机权限，您将不能完成拍照。")
                    .setRationaleConfirmText("确定")
                    .setDeniedCloseButtonText("关闭")
                    .setGotoSettingButtonText("设定")
                    .setPermissionListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted() {
                            Intent intent;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                intent = new Intent(MainActivity.this, Camera2Activity.class);
                            } else {
                                intent = new Intent(MainActivity.this, CameraActivity.class);
                            }
                            mFile = CommonUtils.createFile("mFile");
                            //文件保存的路径和名称
                            intent.putExtra("file", mFile.toString());
                            //拍照时的提示文本
                            intent.putExtra("hint", "");
                            //是否使用整个画面作为取景区域(全部为亮色区域)
                            intent.putExtra("hideBounds", false);
                            startActivityForResult(intent, App.TAKE_PHOTO_CUSTOM);
                        }

                        @Override
                        public void onPermissionDenied(ArrayList<String> arrayList) {

                        }
                    }).setPermissions(new String[]{Manifest.permission.CAMERA})
                    .check();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK && resultCode != 200) return;
        if (requestCode == App.TAKE_PHOTO_CUSTOM) {
            mFile = new File(data.getStringExtra("file"));
            Observable.just(mFile)
                    //将File解码为Bitmap
                    .flatMap((Func1<File, Observable<Bitmap>>) file -> Observable.create(
                            subscriber -> subscriber.onNext(BitmapUtils.decodeFile(file))))
                    //裁剪Bitmap
                    .flatMap((Func1<Bitmap, Observable<Bitmap>>) bitmap -> Observable.create(
                            subscriber -> subscriber.onNext(BitmapUtils.crop(bitmap))))
                    //将Bitmap写入文件
                    .flatMap((Func1<Bitmap, Observable<File>>) bitmap -> Observable.create(
                            subscriber -> subscriber.onNext(BitmapUtils.writeBitmapToFile(bitmap, "mFile"))))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.bindToLifecycle())
                    .subscribe(file -> {
                        mFile = file;
                        Uri uri = Uri.parse("file://" + mFile.toString());
                        ImagePipeline imagePipeline = Fresco.getImagePipeline();
                        //清除该Uri的Fresco缓存. 若不清除，对于相同文件名的图片，Fresco会直接使用缓存而使得Drawee得不到更新.
                        imagePipeline.evictFromMemoryCache(uri);
                        imagePipeline.evictFromDiskCache(uri);
                        FrescoUtils.load("file://" + mFile.toString()).resize(240, 164).into(iv);
                        btnTakepicture.setText("重新拍照");
                        mHasSelectedOnce = true;
                    });
        } else if (requestCode == App.TAKE_PHOTO_SYSTEM) {
            mFile = CommonUtils.createFile("mFile");
            Observable.just(mFile)
                      //读入File，压缩为指定大小的Bitmap
                      .flatMap((Func1<File, Observable<Bitmap>>) file -> Observable.create(
                              subscriber -> subscriber.onNext(BitmapUtils.compressToResolution(file, 1920 * 1080))))
                      //将Bitmap写入文件
                      .flatMap((Func1<Bitmap, Observable<File>>) bitmap -> Observable.create(
                              subscriber -> subscriber.onNext(BitmapUtils.writeBitmapToFile(bitmap, "mFile"))))
                      .subscribeOn(Schedulers.io())
                      .observeOn(AndroidSchedulers.mainThread())
                      .compose(this.bindToLifecycle())
                      .subscribe(file -> {
                          mFile = file;
                          //删除fresco的缓存
                          Uri uri = Uri.parse("file://" + mFile.toString());
                          ImagePipeline imagePipeline = Fresco.getImagePipeline();
                          imagePipeline.evictFromMemoryCache(uri);
                          imagePipeline.evictFromDiskCache(uri);
                          FrescoUtils.load("file://" + mFile.toString()).resize(240, 164).into(iv);
                          btnTakepicture.setText("重新拍照");
                          mHasSelectedOnce = true;
                      });
        }
    }

    @Override
    protected int getContentViewResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void preInitData() {
        registerForContextMenu(btnTakepicture);
    }
}
