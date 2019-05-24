package 多线程实战.nioChatRoom;

import com.sun.org.apache.regexp.internal.RE;
import 并行模式与算法.nio.NioClientDemo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;

public class ChatRoomClient {
    private Selector selector = null;
    private static final int PORT = 9999;
    private Charset charset = Charset.forName("UTF-8");
    private SocketChannel clientChannel = null;
    private String serverName = "192.168.0.113";
    private String name = "";
    private static final String USER_CONTENT_SPILIT = "#";
    public  void init(){
        try {
            //打开多线路复用器
            selector = SelectorProvider.provider().openSelector();
            //建立与服务器的连接通道
            InetSocketAddress remote = new InetSocketAddress(serverName, PORT);
            clientChannel = SocketChannel.open(remote);
            System.out.println(remote.getAddress());
            //配置非阻塞
            clientChannel.configureBlocking(false);
            //注册通道  并让selector对读兴趣。这个通道对对感兴趣
            clientChannel.register(selector, SelectionKey.OP_READ);
            //开启线程进行监听
            new Thread(new ClientThread()).start();
            Scanner input = new Scanner(System.in);
            String line = null;
            while (input.hasNextLine()){
                line = input.nextLine();
                if ("".equals(line))continue;
                if("".equals(name)) {
                    name = line;
                    line = name+USER_CONTENT_SPILIT;
                } else {
                    line = name+USER_CONTENT_SPILIT+line;
                }
                //把读取的数据经过处理写道通道
                clientChannel.write(charset.encode(line));
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //用于读取服务端的信息
    public class ClientThread implements Runnable{
        @Override
        public void run() {
            clientMoniter();
        }
    }

    private void clientMoniter(){
        for (;;){
            if (!selector.isOpen()){
                break;
            }
            try {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()){
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isReadable()){
                        //读取服务端的信息也就是从通道中读取信息
                        doRead(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private final void doRead(SelectionKey key){
        try {
            SocketChannel client =(SocketChannel) key.channel();
            //开辟1k的缓存空间
            ByteBuffer bb =ByteBuffer.allocate(1024);
            String content = "";
            int len;
            while ((len = client.read(bb)) > 0){
                bb.flip();
                content += charset.decode(bb);
            }
            System.out.println(content);
            key.interestOps(SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
