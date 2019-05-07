package mammoth.agv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
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
            textView.setText(textView.getText() + "\n" + str);
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

                    if(etHost.getText() == null || etHost.equals(""))
                    {
                        outputStr = "获取数据失败，host不能为空！";
                        Message Msg3 = new Message();
                        Msg3.obj = outputStr;
                        handlerOutput.sendMessage(Msg3);
                        return;
                    }

                    InetAddress addr = InetAddress.getByName(String.valueOf(etHost.getText()));
                    Socket socket = new Socket(addr, Integer.valueOf(etPort.getText().toString()));

                    OutputStream os = socket.getOutputStream();
                    String sendmsg = "xx" + etX.getText().toString();
                    os.write(sendmsg.getBytes());
                    os.flush();
                    outputStr = "发送消息：“" + sendmsg;

                    sendmsg = "yy" + etY.getText().toString();
                    os.write(sendmsg.getBytes());
                    os.flush();
                    socket.shutdownOutput();
                    outputStr += sendmsg + "”";

                    Message Msg4 = new Message();
                    Msg4.obj = outputStr;
                    handlerOutput.sendMessage(Msg4);
                    /*
                    InputStream is = socket.getInputStream();
                    InputStreamReader reader = new InputStreamReader(is);
                    BufferedReader bufReader = new BufferedReader(reader);
                    String s = null;
                    final StringBuffer sb = new StringBuffer();
                    while((s = bufReader.readLine()) != null){
                        sb.append(s);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            EditText show2 = findViewById(R.id.show2);
                            show2.setText(sb.toString());
                        }
                    });
                    */
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

                    os.close();
                    socket.close();
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
            LinearLayout linearLayoutCamera = findViewById(R.id.colLeft);
            float x = event.getX() / linearLayoutCamera.getWidth();
            float y = event.getY() / linearLayoutCamera.getHeight();
            x *= 1000;
            y *= 1000;
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

}

