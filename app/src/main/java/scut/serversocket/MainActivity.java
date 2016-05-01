package scut.serversocket;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextView tv = null;
    private EditText et = null;
    private TextView IPtv = null;
    private Button btnSend = null;
    private Button btnAcept = null;
    private Button btnSendImage = null;
    private ImageView iv;
    private Socket socket;
    private ServerSocket mServerSocket = null;
    private boolean running = false;
    private AcceptThread mAcceptThread;
    private ReceiveThread mReceiveThread;
    private Handler mHandler = null;
    private AssetManager assets;
    private String[] images;
    private int currentImg = 0;
    private InputStream is;
    private Bitmap bitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);
        et = (EditText) findViewById(R.id.etSend);
        IPtv = (TextView) findViewById(R.id.tvIP);
        btnAcept = (Button) findViewById(R.id.btnAccept);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnSendImage = (Button) findViewById(R.id.btnSendImage);
        iv = (ImageView) findViewById(R.id.iv);
        iv.setOnClickListener(this);
        mHandler = new MyHandler();
        setButtonOnStartState(true);//设置按钮状态
        btnAcept.setOnClickListener(this);
        //发送数据按钮
        btnSend.setOnClickListener(this);
        //发送图片按钮
        btnSendImage.setOnClickListener(this);
        initImages();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnAccept:
                //开始监听线程，监听客户端连接
                mAcceptThread = new AcceptThread();
                running = true;
                mAcceptThread.start();
                setButtonOnStartState(false);
                IPtv.setText("等待连接");
                break;
            case R.id.btnSend:
                OutputStream os = null;
                try {
                    os = socket.getOutputStream();//获得socket的输出流
                    String msg = et.getText().toString() + "\n";
//                    System.out.println(msg);
                    os.write(msg.getBytes("utf-8"));//输出EditText的内容
                    et.setText("");//发送后输入框清0
                    os.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    displayToast("未连接不能输出");//防止服务器端关闭导致客户端读到空指针而导致程序崩溃
                }
                break;
            case R.id.iv:
                System.out.println("第" + currentImg + "张图片");
                ReadImageThread rit = new ReadImageThread();
                rit.start();
                break;
            case R.id.btnSendImage://发送图片
                int size = 0;
                try {
                    os = socket.getOutputStream();
                    os.write(("##*图片*##" + "\n").getBytes("utf-8"));
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    is = new ByteArrayInputStream(baos.toByteArray());


                    size = is.available();
                    byte[] data = new byte[size];
                    System.out.println("size = " + size);
                    is.read(data);
                    dos.writeInt(size);
                    dos.write(data);
                    dos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e){
                    displayToast("你不能发送空图片");
                }
                break;
        }
    }

    //定义监听客户端连接的线程
    private class AcceptThread extends Thread {
        @Override
        public void run() {
//            while (running) {
            try {
                mServerSocket = new ServerSocket(40012);//建立一个ServerSocket服务器端
                socket = mServerSocket.accept();//阻塞直到有socket客户端连接
//                System.out.println("连接成功");
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Message msg = mHandler.obtainMessage();
                msg.what = 0;
                msg.obj = socket.getInetAddress().getHostAddress();//获取客户端IP地址
                mHandler.sendMessage(msg);//返回连接成功的信息
                //开启mReceiveThread线程接收数据
                mReceiveThread = new ReceiveThread(socket);
                mReceiveThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
//            }
        }
    }

    //定义接收数据的线程
    private class ReceiveThread extends Thread {
        private InputStream is = null;
        private String read;

        //建立构造函数来获取socket对象的输入流
        public ReceiveThread(Socket sk) {
            try {
                is = sk.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                try {
                    //读服务器端发来的数据，阻塞直到收到结束符\n或\r
                    read = br.readLine();
                    System.out.println(read);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    running = false;//防止服务器端关闭导致客户端读到空指针而导致程序崩溃
                    Message msg2 = mHandler.obtainMessage();
                    msg2.what = 2;
                    mHandler.sendMessage(msg2);//发送信息通知用户客户端已关闭
                    e.printStackTrace();
                    break;
                }
                //用Handler把读取到的信息发到主线程
                Message msg = mHandler.obtainMessage();
                msg.what = 1;
                msg.obj = read;
                mHandler.sendMessage(msg);

            }
        }
    }

    private void displayToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    class MyHandler extends Handler {//在主线程处理Handler传回来的message

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    String str = (String) msg.obj;
                    tv.setText(str);
                    break;
                case 0:
                    IPtv.setText("客户端" + msg.obj + "已连接");
                    displayToast("连接成功");
                    break;
                case 2:
                    displayToast("客户端已断开");
                    //清空TextView
                    tv.setText(null);//
                    IPtv.setText(null);
                    try {
                        socket.close();
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    setButtonOnStartState(true);
                    break;
                case 3:
                    //改变ImageView显示的图片
                    is = (InputStream) msg.obj;
                    try {
                        System.out.println(is.available());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    iv.setImageBitmap(bitmap = BitmapFactory.decodeStream((InputStream) msg.obj));
                    System.out.println("显示完毕");
                    try {
                        System.out.println(is.available());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);//清空消息队列，防止Handler强引用导致内存泄漏
    }

    public void initImages() {//初始化图像
        assets = getAssets();
        //获取/assets/目录下所有文件
        try {
            images = assets.list("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class ReadImageThread extends Thread {
        @Override
        public void run() {
            //如果发生数组越界
            if (currentImg >= images.length) {
                currentImg = 0;
            }
            //找到下一个图片文件
            while (!images[currentImg].endsWith(".png")
                    && !images[currentImg].endsWith(".jpg")
                    && !images[currentImg].endsWith(".gif")) {
                currentImg++;
                //如果已发生数组越界
                if (currentImg >= images.length) {
                    currentImg = 0;
                }
            }
            InputStream assetFile = null;
            try {
                //打开指定的资源对应的输入流
                assetFile = assets.open(images[currentImg++]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            BitmapDrawable bitmapDrawable = (BitmapDrawable) iv.getDrawable();
            //如果图片还未回收,先强制回收该图片　
            if (bitmapDrawable != null &&
                    !bitmapDrawable.getBitmap().isRecycled()) {
                bitmapDrawable.getBitmap().recycle();
            }
            Message msg3 = mHandler.obtainMessage();
            msg3.what = 3;
            msg3.obj = assetFile;
            mHandler.sendMessage(msg3);
            System.out.println("加载完毕");

        }
    }

    private void setButtonOnStartState(boolean flag) {//设置按钮的状态

        btnAcept.setEnabled(flag);
        btnSend.setEnabled(!flag);
        btnSendImage.setEnabled(!flag);
    }
}