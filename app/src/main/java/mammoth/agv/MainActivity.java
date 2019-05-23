package mammoth.agv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ImageView imageCamera;
    boolean isRev = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideActionBar();
        setFullScreen();

        imageCamera = findViewById(R.id.imageCamera);
        ImageListener imglistener = new ImageListener();
        imageCamera.setOnTouchListener(imglistener);

        Button btn_accept = findViewById(R.id.buttonSend);
        btn_accept.setOnClickListener(this);

        final TextView logView = findViewById(R.id.textOutput);
        logView.setMovementMethod(ScrollingMovementMethod.getInstance());

        // 获取之前存储的信息
        SharedPreferences read = getSharedPreferences("HostAndPort", MODE_PRIVATE);
        String value = read.getString("host", "");
        EditText etHost = findViewById(R.id.textHost);
        etHost.setText(value);
        value = read.getString("port", "");
        EditText etPort = findViewById(R.id.textPort);
        etPort.setText(value);

        // 设置定时器
        final Timer timer = new Timer();
        TimerTask task;

        // 添加开关监听
        Switch switch_isrev = findViewById(R.id.switchUpdate);
        switch_isrev.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isRev = true;
                } else {
                    isRev = false;
                }
            }
        });

        // 计时器任务，每隔400ms尝试获取图片
        task = new TimerTask() {
            @Override
            public void run() {
                if (!isRev)
                    return;

                new Thread() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        try {
                            // String outputStr;
                            final String endline = ("\n********************");
                            outputMsg("开始连接（摄像头）...", 1);

                            EditText etHost = findViewById(R.id.textHost);
                            EditText etPort = findViewById(R.id.textPort);
                            outputMsg("获取连接数据...", 1);

                            // 检查IP格式合法性
                            if (TextUtils.isEmpty(etHost.getText())) {
                                outputMsg("host不能为空！" + endline, 1);
                                return;
                            }
                            String hostText = String.valueOf(etHost.getText()).trim();
                            boolean hostflag = true;
                            int hostcnt = 0, dotcnt = 0;
                            for (int i = 0; i < hostText.length(); ++i) {
                                if (hostcnt > 3 || dotcnt > 3) {
                                    hostflag = false;
                                    break;
                                } else if (hostText.charAt(i) == '.') {
                                    if (i == 0 || i == hostText.length() - 1) {
                                        hostflag = false;
                                        break;
                                    }
                                    hostcnt = 0;
                                    ++dotcnt;
                                    continue;
                                } else if (!Character.isDigit(hostText.charAt(i))) {
                                    hostflag = false;
                                    break;
                                } else {
                                    ++hostcnt;
                                }
                            }
                            if (dotcnt != 3 || hostcnt > 3)
                                hostflag = false;
                            if (!hostflag) {
                                outputMsg("host格式错误！" + endline, 1);
                                return;
                            }
                            // 保存IP数据
                            SharedPreferences.Editor editor = getSharedPreferences("HostAndPort", MODE_PRIVATE).edit();
                            editor.putString("host", hostText);
                            editor.commit();

                            // 检查端口格式合法性
                            if (TextUtils.isEmpty(etPort.getText())) {
                                outputMsg("port不能为空！" + endline, 1);
                                return;
                            }
                            String portText = String.valueOf(etPort.getText()).trim();
                            if (!isNumeric(portText)) {
                                outputMsg("端口数据错误，应为整数！" + endline, 1);
                                return;
                            }
                            if (Integer.valueOf(portText) < 0 || Integer.valueOf(portText) > 65535) {
                                outputMsg("端口范围错误！0≤port≤65535！" + endline, 1);
                                return;
                            }
                            // 保存端口数据
                            editor.putString("port", portText);
                            editor.commit();

                            // 新建socket
                            InetAddress addr = InetAddress.getByName(hostText);
                            Socket socket = new Socket(addr, Integer.valueOf(portText));

                            // 此处必须有输出流，否则输入流一直为空，原因不明
                            OutputStream os = socket.getOutputStream();
                            String sendmsg = "@";
                            os.write(sendmsg.getBytes());
                            socket.shutdownOutput();

                            // 图片传输
                            outputMsg("开始接收图片...", 1);
                            DataInputStream dataInput = new DataInputStream(socket.getInputStream());
                            List<Byte> list = new ArrayList<>();
                            int size = 1024;// 每次传输1024byte
                            byte[] data = new byte[size];
                            int len;
                            // 读入直到输入流为空
                            while ((len = dataInput.read(data, 0, data.length)) > 0) {
                                // 重要：把len作为参数传进去，
                                // 由于网络原因，DataInputStream.read并不是每次都返回data.size个字节，有可能比data.size小，
                                // 那么data[]后面的字节填充的是无效数据，要把它忽略掉，否则图片有马赛克
                                appendByteArrayIntoByteList(list, data, len);
                            }
                            // 将数据转为byte数组
                            Byte[] IMG = list.toArray(new Byte[0]);
                            byte[] img = new byte[IMG.length];
                            for (int i = 0; i < IMG.length; i++) {
                                img[i] = IMG[i];
                            }
                            // 将数据转为bitmap图像
                            ByteArrayOutputStream outPut = new ByteArrayOutputStream();
                            Bitmap bmp = BitmapFactory.decodeByteArray(img, 0, img.length);
                            if (bmp == null) {
                                outputMsg("未获取到图片！", 1);
                                return;
                            }
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, outPut);
                            // 传回主线程
                            Message msg = new Message();
                            msg.obj = bmp;
                            handlerSetImg.sendMessage(msg);
                            outputMsg("图片已接收！", 1);

                            // 关闭输出流，关闭socket连接
                            os.close();
                            socket.close();
                            outputMsg(endline, 1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Looper.loop();
                    }
                }.start();
            }
        };
        // 启动定时器
        timer.schedule(task, 400, 400);
    }

    // 缩放图片填充到画布中
    Handler handlerSetImg = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (isRev == false)
                return;
            Bitmap model = (Bitmap) msg.obj;
            ImageView imageView = findViewById(R.id.imageCamera);
            Bitmap dst = Bitmap.createScaledBitmap(model, imageView.getHeight(), imageView.getWidth(), true);
            imageView.setImageBitmap(dst);
        }
    };

    // 输出状态信息
    Handler handlerOutput = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (isRev == false && msg.what == 1)
                return;
            String str = (String) msg.obj;
            TextView textView = findViewById(R.id.textOutput);
            // textView.setText(textView.getText() + "\n" + str);
            textView.append("\n" + str);
            int offset = textView.getLineCount() * textView.getLineHeight();
            if (offset > textView.getHeight()) {
                textView.scrollTo(0, offset - textView.getHeight());
            }
        }
    };

    // 发送坐标信息
    @Override
    public void onClick(View v) {
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                try {
                    String outputStr;
                    final String endline = ("\n********************");
                    outputMsg("开始连接（发送坐标）...", 2);

                    EditText etX = findViewById(R.id.textX);
                    EditText etY = findViewById(R.id.textY);
                    EditText etHost = findViewById(R.id.textHost);
                    EditText etPort = findViewById(R.id.textPort);
                    outputMsg("获取连接数据...", 2);

                    // 检查IP格式合法性
                    if (TextUtils.isEmpty(etHost.getText())) {
                        outputMsg("host不能为空！" + endline, 2);
                        return;
                    }
                    String hostText = String.valueOf(etHost.getText()).trim();
                    boolean hostflag = true;
                    int hostcnt = 0, dotcnt = 0;
                    for (int i = 0; i < hostText.length(); ++i) {
                        if (hostcnt > 3 || dotcnt > 3) {
                            hostflag = false;
                            break;
                        } else if (hostText.charAt(i) == '.') {
                            if (i == 0 || i == hostText.length() - 1) {
                                hostflag = false;
                                break;
                            }
                            hostcnt = 0;
                            ++dotcnt;
                            continue;
                        } else if (!Character.isDigit(hostText.charAt(i))) {
                            hostflag = false;
                            break;
                        } else {
                            ++hostcnt;
                        }
                    }
                    if (dotcnt != 3 || hostcnt > 3)
                        hostflag = false;
                    if (!hostflag) {
                        outputMsg("host格式错误！" + endline, 2);
                        return;
                    }
                    // 保存IP数据
                    SharedPreferences.Editor editor = getSharedPreferences("HostAndPort", MODE_PRIVATE).edit();
                    editor.putString("host", hostText);
                    editor.commit();

                    // 检查端口格式合法性
                    if (TextUtils.isEmpty(etPort.getText())) {
                        outputMsg("port不能为空！" + endline, 2);
                        return;
                    }
                    String portText = String.valueOf(etPort.getText()).trim();
                    if (!isNumeric(portText)) {
                        outputMsg("端口数据错误，应为整数！" + endline, 2);
                        return;
                    }
                    if (Integer.valueOf(portText) < 0 || Integer.valueOf(portText) > 65535) {
                        outputMsg("端口范围错误！0≤port≤65535！" + endline, 2);
                        return;
                    }
                    // 保存端口数据
                    editor.putString("port", portText);
                    editor.commit();

                    // 检查坐标是否为空
                    if (TextUtils.isEmpty(etX.getText()) || TextUtils.isEmpty(etY.getText())) {
                        outputMsg("坐标不能为空！" + endline, 2);
                        return;
                    }
                    // 新建socket
                    InetAddress addr = InetAddress.getByName(hostText);
                    Socket socket = new Socket(addr, Integer.valueOf(portText));

                    // 发送坐标信息
                    OutputStream os = socket.getOutputStream();
                    String sendmsg = "xx" + etX.getText().toString().trim();
                    os.write(sendmsg.getBytes());
                    os.flush();
                    outputStr = "发送消息：" + sendmsg;

                    sendmsg = "yy" + etY.getText().toString().trim();
                    os.write(sendmsg.getBytes());
                    os.flush();
                    socket.shutdownOutput();
                    outputStr += sendmsg;
                    outputMsg(outputStr, 2);

                    // 图片传输，此处必须有输入流，否则线程中止，原因不明
                    outputMsg("开始接收图片...", 1);
                    DataInputStream dataInput = new DataInputStream(socket.getInputStream());
                    List<Byte> list = new ArrayList<>();
                    int size = 1024;// 每次传输1024byte
                    byte[] data = new byte[size];
                    int len;
                    // 读入直到输入流为空
                    while ((len = dataInput.read(data, 0, data.length)) > 0) {
                        // 重要：把len作为参数传进去，
                        // 由于网络原因，DataInputStream.read并不是每次都返回data.size个字节，有可能比data.size小，
                        // 那么data[]后面的字节填充的是无效数据，要把它忽略掉，否则图片有马赛克
                        appendByteArrayIntoByteList(list, data, len);
                    }
                    // 将数据转为byte数组
                    Byte[] IMG = list.toArray(new Byte[0]);
                    byte[] img = new byte[IMG.length];
                    for (int i = 0; i < IMG.length; i++) {
                        img[i] = IMG[i];
                    }
                    // 将数据转为bitmap图像
                    ByteArrayOutputStream outPut = new ByteArrayOutputStream();
                    Bitmap bmp = BitmapFactory.decodeByteArray(img, 0, img.length);
                    if (bmp == null) {
                        outputMsg("未获取到图片！", 1);
                        return;
                    }
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outPut);
                    // 传回主线程
                    Message msg = new Message();
                    msg.obj = bmp;
                    handlerSetImg.sendMessage(msg);
                    outputMsg("图片已接收！", 1);

                    // 关闭输出流，关闭socket连接
                    os.close();
                    socket.close();
                    outputMsg(endline, 2);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Looper.loop();
            }
        }.start();
    }

    // 获取触摸点对应坐标（按比例），并赋值到文本编辑框中
    class ImageListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            LinearLayout linearLayoutCamera = findViewById(R.id.colMid);
            float x = event.getX() / linearLayoutCamera.getWidth();
            float y = event.getY() / linearLayoutCamera.getHeight();

            int maxsize = 1000;
            x *= maxsize;
            y *= maxsize;
            if (x < 0)
                x = 0;
            if (y < 0)
                y = 0;
            if (x > maxsize)
                x = maxsize;
            if (y > maxsize)
                y = maxsize;

            EditText editX = findViewById(R.id.textX);
            editX.setText(String.valueOf((int) x));
            EditText editY = findViewById(R.id.textY);
            editY.setText(String.valueOf((int) y));

            return true;
        }
    }

    // 输出信息到log, what：1与isRev相关，2与isRev无关
    private void outputMsg(String str, int what) {
        Message Msg = new Message();
        Msg.obj = str;
        Msg.what = what;
        handlerOutput.sendMessage(Msg);
    }

    // 隐藏状态栏
    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    // 应用全屏
    private void setFullScreen() {
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    // 判断字符串是否全为数字
    public static boolean isNumeric(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 将字节数组添加到字节链表中
    private void appendByteArrayIntoByteList(List<Byte> list, byte[] array, int count) {
        for (int i = 0; i < count; ++i) {
            list.add(array[i]);
        }
        return;
    }
}
