package 多线程实战.nioChatRoom;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatRoomServer {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    //输入格式的协议
    private static final String MSG_SPLIT = "#";
    //定义一个保存用户的容器
    public static volatile Vector<String> vector = new Vector<>();
    //用户状态
    private static final Integer ON_LINE = 0;
    private static final Integer ON_OUTLINE = 1;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private Charset charset = Charset.forName("UTF-8");
    private static final int port = 9999;

    //初始化
    public void init(){
        try {
            //打开服务端的通道
            serverSocketChannel = ServerSocketChannel.open();
            //打开多线路复用器
            selector = SelectorProvider.provider().openSelector();
            //配置为非阻塞
            serverSocketChannel.configureBlocking(false);
            //InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), port);
            InetSocketAddress address = new InetSocketAddress(port);
            System.out.println(InetAddress.getLocalHost()+"   Port:"+port);
            //监听端口
            serverSocketChannel.bind(address);
            //把服务器端 的通道注册到selecter
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            monitor();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                //关闭通道和
                serverSocketChannel.close();
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //监听通道
    public void monitor() throws IOException {
        System.out.println("Server is listening···");
        //死循环 一直监听
        for (;;){
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();
            while (keyIterator.hasNext()){
                SelectionKey sk = keyIterator.next();
                //一处已经处理的SelectorKey
                keyIterator.remove();
                if (sk.isAcceptable()){
                    doAccept(sk);
                }else if (sk.isReadable()){
                    doRead(sk);
                }
            }
        }
    }
    //当佑一个客户端接入时，就会产生一个新的channal代表这个连接
    private void doAccept(SelectionKey key){
        ServerSocketChannel serverSocketChannel  = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel;
        try {
            //该通道表示和客户端通信的通道
            clientChannel  = serverSocketChannel.accept();
            //配置为 非阻塞
            clientChannel.configureBlocking(false);
            //注册
            clientChannel.register(selector,SelectionKey.OP_READ);
            //设置该key 为其他客户端连接感兴趣
            key.interestOps(SelectionKey.OP_ACCEPT);
            System.out.println("Server is listening from client :" + clientChannel.getRemoteAddress());
            clientChannel.write(charset.encode("please input your name:"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //处理客户端的数据
    private void doRead(SelectionKey key) throws IOException{
        SocketChannel clientChannel = (SocketChannel) key.channel();
        //分配8k的缓冲区
        ByteBuffer bb = ByteBuffer.allocate(8192);
        StringBuffer content = new StringBuffer();
        int len;
        try {
            len = clientChannel.read(bb);
            //读取客户端额的数据
            if (len == -1){
                key.interestOps(SelectionKey.OP_CONNECT);
                return;
            }else {
                //切换到读模式
                bb.flip();
                content.append(charset.decode(bb));
            }
            System.out.println("Server is listening from client " + clientChannel.getRemoteAddress() + " data receive is: " + content);
            //设置这个key对读感兴趣 ，为下一次读做准备
            key.interestOps(SelectionKey.OP_READ);
            //提交给线程池处理
            pool.execute(new HandleMsg(clientChannel.socket().getInetAddress().getHostName(),key,content));
        } catch (IOException e) {
            key.cancel();
            if (key.channel() != null){
                key.cancel();
            }
        }
    }
    //模拟对接受客户端 的数据进行处理
    class HandleMsg extends Thread{
        private SelectionKey key;
        private StringBuffer content;
        public HandleMsg(String name,SelectionKey key, StringBuffer content){
            super.setName(name);
            this.key = key;
            this.content = content;
        }
        @Override
        public void run() {
            SocketChannel clientChannel = (SocketChannel) key.channel();
            if (content.length() > 0){
                String[] contents = content.toString().split(MSG_SPLIT);
                //新用户注册
                if (contents != null && contents.length == 1){
                    String name = contents[0];
                    if (vector.contains(name)){
                        try {
                            clientChannel.write(charset.encode("name ("+name +") is already used ! ,please change"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else {
                        vector.add(name);
                        String msg = "hello! "+name+" welcome come to chatRoom";
                        //广播信息
                        broadCastMsg(selector,null,msg);
                    }
                }else if (contents != null && contents.length > 1){
                    String name = contents[0];
                    String msg = content.substring(name.length()+MSG_SPLIT.length());
                    msg = "["+name+"]" + "  >> : " +msg;
                    //对于自己就不需要广播
                    if (vector.contains(name)){
                        broadCastMsg(selector,clientChannel,msg);
                    }
                }
            }
        }
    }
    //广播消息
    protected void broadCastMsg(Selector selector,SocketChannel channel,String content){
        for (SelectionKey key : selector.keys()){
            //获取每个通道
            Channel clentChannel = key.channel();
            if (clentChannel instanceof SocketChannel && clentChannel != channel){
                try {
                    SocketChannel cs = (SocketChannel) clentChannel;
                    cs.write(charset.encode(content));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
