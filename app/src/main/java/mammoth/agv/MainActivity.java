package mammoth.agv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ImageView imageCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideActionBar();
        setFullScreen();

        imageCamera = findViewById(R.id.imageCamera);
        ImageListener imglistener = new ImageListener();
        imageCamera.setOnTouchListener(imglistener);

        Button btn_accept = (Button) findViewById(R.id.buttonSend);
        btn_accept.setOnClickListener(this);

        final TextView logView=(TextView)findViewById(R.id.textOutput);
        logView.setMovementMethod(ScrollingMovementMethod.getInstance());

        SharedPreferences read = getSharedPreferences("HostAndPort", MODE_PRIVATE);
        String value = read.getString("host", "");
        EditText etHost = findViewById(R.id.textHost);
        etHost.setText(value);
        value = read.getString("port", "");
        EditText etPort = findViewById(R.id.textPort);
        etPort.setText(value);
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bitmap model = (Bitmap) msg.obj;
            ImageView imageView = findViewById(R.id.imageCamera);
            imageView.setImageBitmap(model);
        }
    };

    Handler handlerOutput = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String str = (String) msg.obj;
            TextView textView = findViewById(R.id.textOutput);
            //textView.setText(textView.getText() + "\n" + str);
            textView.append("\n" + str);
            int offset=textView.getLineCount()*textView.getLineHeight();
            if(offset>textView.getHeight()){
                textView.scrollTo(0,offset-textView.getHeight());
            }
        }
    };
    @Override
    public void onClick(View v) {
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                try {
                    String outputStr = new String();
                    final String endline = new String("\n********************");
                    outputStr = "开始连接...";
                    Message Msg1 = new Message();
                    Msg1.obj = outputStr;
                    handlerOutput.sendMessage(Msg1);

                    outputStr = "获取数据...";
                    Message Msg2 = new Message();
                    Msg2.obj = outputStr;
                    handlerOutput.sendMessage(Msg2);

                    EditText etX = findViewById(R.id.textX);
                    EditText etY = findViewById(R.id.textY);
                    EditText etHost = findViewById(R.id.textHost);
                    EditText etPort = findViewById(R.id.textPort);

                    if (TextUtils.isEmpty(etHost.getText())) {
                        outputStr = "host不能为空！" + endline;
                        Message Msg311 = new Message();
                        Msg311.obj = outputStr;
                        handlerOutput.sendMessage(Msg311);
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
                    if (dotcnt != 3 || hostcnt > 3) hostflag = false;
                    if (!hostflag) {
                        outputStr = "host格式错误！" + endline;
                        Message Msg312 = new Message();
                        Msg312.obj = outputStr;
                        handlerOutput.sendMessage(Msg312);
                        return;
                    }
                    SharedPreferences.Editor editor = getSharedPreferences("HostAndPort", MODE_PRIVATE).edit();
                    editor.putString("host", hostText);
                    editor.commit();

                    if (TextUtils.isEmpty(etPort.getText())) {
                        outputStr = "port不能为空！" + endline;
                        Message Msg32 = new Message();
                        Msg32.obj = outputStr;
                        handlerOutput.sendMessage(Msg32);
                        return;
                    }

                    String portText = String.valueOf(etPort.getText()).trim();
                    if(!isNumeric(portText)) {
                        outputStr = "端口数据错误，应为整数！" + endline;
                        Message Msg321 = new Message();
                        Msg321.obj = outputStr;
                        handlerOutput.sendMessage(Msg321);
                        return;
                    }
                    if(Integer.valueOf(portText) < 0 || Integer.valueOf(portText) > 65535) {
                        outputStr = "端口范围错误！0<=port<=65535！" + endline;
                        Message Msg322 = new Message();
                        Msg322.obj = outputStr;
                        handlerOutput.sendMessage(Msg322);
                        return;
                    }
                    editor.putString("port", portText);
                    editor.commit();

                    if (TextUtils.isEmpty(etX.getText()) || TextUtils.isEmpty(etY.getText())) {
                        outputStr = "坐标不能为空！" + endline;
                        Message Msg33 = new Message();
                        Msg33.obj = outputStr;
                        handlerOutput.sendMessage(Msg33);
                        return;
                    }

                    InetAddress addr = InetAddress.getByName(hostText);
                    Socket socket = new Socket(addr, Integer.valueOf(portText));

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

                    Message Msg4 = new Message();
                    Msg4.obj = outputStr;
                    handlerOutput.sendMessage(Msg4);

                    InputStream is = socket.getInputStream();
                    InputStreamReader reader = new InputStreamReader(is);
                    BufferedReader bufReader = new BufferedReader(reader);
                    String s = null;
                    final StringBuffer sb = new StringBuffer();
                    while((s = bufReader.readLine()) != null){
                        sb.append(s);
                    }
                    Message Msg5 = new Message();
                    Msg5.obj = ("接受消息：" + sb.toString());
                    handlerOutput.sendMessage(Msg5);
                    /*
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //EditText show2 = findViewById(R.id.show2);
                            //show2.setText(sb.toString());

                        }
                    });
                    */
                    /*
                    outputStr = "开始接收图片...";
                    Message Msg5 = new Message();
                    Msg5.obj = outputStr;
                    handlerOutput.sendMessage(Msg5);
                    DataInputStream dataInput = new DataInputStream(socket.getInputStream());
                    int size = dataInput.readInt();
                    byte[] data = new byte[size];
                    int len = 0;
                    while (len < size) {
                        len += dataInput.read(data, len, size - len);
                    }
                    ByteArrayOutputStream outPut = new ByteArrayOutputStream();
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outPut);

                    outputStr = "图片已接收！";
                    Message Msg6 = new Message();
                    Msg6.obj = outputStr;
                    handlerOutput.sendMessage(Msg6);

                    socket.close();
                    Message msg = new Message();
                    msg.what = 0x002;
                    msg.obj = bmp;
                    handler.sendMessage(msg);
*/
                    os.close();
                    socket.close();


                    outputStr = endline;
                    Message Msg0 = new Message();
                    Msg0.obj = outputStr;
                    handlerOutput.sendMessage(Msg0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Looper.loop();
            }
        }.start();
    }
    class ImageListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            LinearLayout linearLayoutCamera = findViewById(R.id.colMid);
            float x = event.getX() / linearLayoutCamera.getWidth();
            float y = event.getY() / linearLayoutCamera.getHeight();
            x *= 1000;
            y *= 1000;
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x > 1000) x = 1000;
            if (y > 1000) y = 1000;
            EditText editX = findViewById(R.id.textX);
            editX.setText(String.valueOf((int)x));
            EditText editY = findViewById(R.id.textY);
            editY.setText(String.valueOf((int)y));

            return true;
        }
    }

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void setFullScreen() {
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static boolean isNumeric(String str)
    {
        for (int i = 0; i < str.length(); i++)
        {
            //System.out.println(str.charAt(i));
            if (!Character.isDigit(str.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }
}

