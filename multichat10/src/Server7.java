
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by cmina on 2017-05-21.
 */
public class Server7 {

    //네트워크 자원
    private ServerSocket serverSocket;
    private Socket socket;

    //사용자가 접속할 때마다 벡터에 유저정보 저장
    //private static Vector user_vector = new Vector();

    private List<UserInfo> userList = new ArrayList<UserInfo>();

    //private static Vector room_vector = new Vector();
    private List<RoomInfo> roomList = new ArrayList<RoomInfo>();

    private void Server_Start() {
        try {
            serverSocket = new ServerSocket(12345);  //12345번 포트 사용
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("이미 사용중인 포트입니다");
        }

        if (serverSocket != null) { //정상적으로 포트가 열렸을 경우
            Connection(); //사용자가 접속하는 부분
        }

    }

    private void Connection() {

        //1가지의 스레드에서는 1가지의 일만 처리할 수 있다
        //스레드로 하지 않으면, 사용자의 접속을 무한정 대기. 랙이 걸린다.
        //사용자 기다리는 스레드 따로,
        //사용자가 접속했을 때, UserInfo스레드 돌리는 부분 따로 하기 위해서
        //스레드로 ..돌린다.
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() { //스레드에서 처리할 일을 기재
                System.out.println("서버7 시작");

                while (true) {
                    try {
                        socket = serverSocket.accept(); //사용자 접속 대기 무한대기

                        //사용자가 접속하면 UserInfo객체를 만들어주는데, 사용자가 접속한 소켓정보를 넣어준다
                        UserInfo userInfo = new UserInfo(socket);
                        userInfo.start(); //개별 스레드 실행

                    } catch (IOException e) {
                        // e.printStackTrace();
                        System.out.println("accept 에러 발생");
                        break;
                    }
                }

            }
        });

        thread.start();


    }

    public static void main(String[] args) {

        Server7 server = new Server7();
        server.Server_Start();

    }

    //사용자 클래스
    class UserInfo extends Thread {

        private Socket user_socket;
        private String nickName = ""; //채팅에 접속한 유저마다 개별 쓰레드. 개별적인 닉네임.

        private OutputStream os;
        private InputStream is;
    /*    private DataOutputStream dos;
        private DataInputStream dis;
*/
        UserInfo(Socket socket) { //생성자 메소드
            this.user_socket = socket;
            UserNetwork();
        }

        private void UserNetwork() { //네트워크 자원설정
            try {
                is = user_socket.getInputStream();
            //    dis = new DataInputStream(is);

                os = user_socket.getOutputStream();
            //    dos = new DataOutputStream(os);
                System.out.println("클라이언트 접속" +  user_socket);

                userList.add(this);
                Collections.synchronizedList(userList);
                //list를 사용한다면.....synchronized해줘야....
                System.out.println("현재 접속된 사용자 수 : " + userList.size());


            } catch (IOException e) {
                //e.printStackTrace();
                System.out.println("Stream 설정 에러 발생");
            }

        }

        int left_packet = 0;

        public void run() { //스레드로 처리할 내용. 클라이언트에서 들어오는 메시지 개별적으로 받음

            while (true) {
                try {
                    //프로토콜 객체를 바로 만드는게 아니라,,,
                    //보내온 길이만큼 버퍼에 담아서,
                    //protocol.getPacket에 넣기.
                    //받은 길이와, 데이터의 길이를 비교
                    //   while (is.available() > 0) { //읽을 게 있을 때만.. //while문에 들어가니까 IOException못잡네..
                    if (is.available() <= 0 && left_packet <= 0) { //읽을 게 없으면, 밑에 부분 실행하지 않고, 위에 while(ture) 로 가기
                        continue;
                    }

                    System.out.println("인풋스트림 크기 : " + is.available());
                    //////////////////
                    //새 protocol 객체생성(기본생성자)
                    Protocol protocol;

                    byte[] buf;

                    //이전에 받은 패킷이 남아있다면
                    if (left_packet > 0) {
                        protocol = new Protocol(left_packet);
                        buf = protocol.getPacket();
                        System.out.println("남은 패킷 길이 : " + left_packet);

                        left_packet = 0; //다시 0으로 초기화


                        is.read(buf);

                        //여기서..자꾸에러나네....
                        int total_len = Integer.parseInt(protocol.getTotalLen()); //받아야할 길이

                        int recv_len = buf.length; //받은 길이
                        int recv_cnt = 0;

                        System.out.println("인풋스트림 읽기시작 받아야할 길이 : " + total_len + "//buf.length" + recv_len);

                        if (total_len > recv_len) { //받아야할 길이가 받은 길이보다 크면,
                            protocol.setPacket2(total_len, buf);
                        } else {
                            protocol.setPacket(total_len, buf); //total크기의 바이트배열에다가 지금까지 받은 buf(바이트배열)을 복사하고,
                        }

                        while (recv_len < total_len) { //받아야할 길이보다 덜 받았으면....
                            if (is.available() > 0) {
                                buf = new byte[is.available()];
                                System.out.println(" 계속 읽는 중" + is.available());

                                recv_cnt = is.read(buf); //인풋스트림에서 읽어와서 buf바이트배열에 담는다.
                                System.out.println("더 읽은 데이터 길이 : " + recv_cnt);

                                //남은 길이 비교해서,
                                //남은길이가 더 크면
                                if ((total_len - recv_len) >= recv_cnt) {
                                    protocol.addPacket(recv_len, buf);
                                    recv_len += recv_cnt; //읽은 데이터 길이를 더한다.

                                } else {
                                    protocol.addPacket2(total_len, recv_len, buf);
                                    recv_len += (total_len - recv_len); //읽은 데이터 길이를 더한다.

                                    left_packet = recv_cnt - (total_len - recv_len);
                                    //left_packet = buf.length - (total_len - recv_len);
                                }

                                System.out.println("지금까지 읽은 데이터 길이 : " + recv_len);
                            }
                        }

                    } else {
                        //기본 생성자로 생성하면, 바이트배열의 길이 4096로 지정
                        //인풋스트림에서 읽을 게 있는 만큼.....

                        protocol = new Protocol(is.available());
                        buf = protocol.getPacket();
                        //버퍼의 크기는 4096이네...is.available()크기가 아니라...왜지?
                        System.out.println("left 0일 떄, 인풋스트림에서 읽어온길이 : " + buf.length);


                        is.read(buf);

                        //여기서..자꾸에러나네....
                        int total_len = Integer.parseInt(protocol.getTotalLen()); //받아야할 길이

                        int recv_len = buf.length; //받은 길이
                        int recv_cnt = 0;

                        System.out.println("left 0일 떄, 인풋스트림 읽기시작 받아야할 길이 : " + total_len + "//buf.length" + recv_len);

                        if (total_len > recv_len) { //받아야할 길이가 받은 길이보다 크면,
                            protocol.setPacket2(total_len, buf);
                        } else {
                            protocol.setPacket(total_len, buf); //total크기의 바이트배열에다가 지금까지 받은 buf(바이트배열)을 복사하고,
                        }

                        while (recv_len < total_len) { //받아야할 길이보다 덜 받았으면....
                            if (is.available() > 0) {
                                buf = new byte[is.available()];
                                System.out.println("left 0일 떄, 계속 읽는 중" + is.available());

                                recv_cnt = is.read(buf); //인풋스트림에서 읽어와서 buf바이트배열에 담는다.
                                System.out.println("left 0일 떄, 더 읽은 데이터 길이 : " + recv_cnt);

                                //남은 길이 비교해서,
                                if ((total_len - recv_len) >= recv_cnt) {
                                    protocol.addPacket(recv_len, buf);
                                    recv_len += recv_cnt; //읽은 데이터 길이를 더한다.

                                } else {
                                    protocol.addPacket2(total_len, recv_len, buf);
                                    recv_len += (total_len - recv_len); //읽은 데이터 길이를 더한다.
                                    //   left_packet = buf.length - (total_len - recv_len);

                                    left_packet = recv_cnt - (total_len - recv_len);

                                }

                                System.out.println("지금까지 읽은 데이터 길이 : " + recv_len);
                            }
                        }
                    }

                    //패킷타입을 얻고 protocol객체의 packet 멤버변수에 buf를 복사.
                    int packetType = Integer.parseInt(protocol.getProtocolType());
                    //여기서 에러
                    inmessage(packetType, protocol);
                    ///////////////////////
                } catch (IOException e) {
                    e.printStackTrace();
                    //클라이언트의 접속이 끊어졌을 때, 에러발생.
                    System.out.println(nickName + "사용자 접속끊어짐");

                    try {
                        //그 사용자와 연결된 스트림 닫아줌
                        //  dos.close();
                        //    dis.close();
                        is.close();
                        os.close();
                        user_socket.close();
                        //user_vector.remove(this);
                        userList.remove(this);
                        //   BroadCast("User_out|" + nickName);

                    } catch (IOException e1) {
                    }

                    System.out.println("현재 접속된 사용자 수 : " + userList.size());
                    break;

                }
            }

        } //run메소드 끝


        private void send_packet(byte[] packet) {
            try {
                os.write(packet);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void inmessage(int protocoltype, Protocol protocol) {


            String roomid = protocol.getRoomid();
            String userid = protocol.getUserid();

            switch (protocoltype) {
                //프로토콜 타입 정의 다시1!!
                case Protocol.PT_CREATEROOM:

                    if (!roomid.equals("")) {
                        RoomInfo new_room = new RoomInfo(roomid, this);
                        roomList.add(new_room);
                        Collections.synchronizedList(roomList); //roomlist동기화
                        System.out.println(roomid + "방생성");
                    }

                    //방을 잘 만들었다는 ok사인?

                    break;

                case Protocol.PT_JOINROOM:
                    for (int i = 0; i < roomList.size(); i++) {
                        RoomInfo r = roomList.get(i);
                        if (r.Room_name.equals(roomid)) {


                            String msgtype = protocol.Message;
                            String msg = userid + "입장했습니다";

                            protocol = new Protocol(msg.getBytes().length + 114);
                            protocol.setProtocolType("2");
                            protocol.setTotalLen(String.valueOf(msg.trim().getBytes().length + 114));
                            //protocol.setDataLength(String.valueOf(msg.trim().getBytes().length));
                            protocol.setMsgType(msgtype);
                            protocol.setRoomid(roomid);
                            protocol.setUserid("알림");
                            protocol.setUserimg("");
                            protocol.setMsg(msg);

                            System.out.println(msg.trim().getBytes().length + "/" + roomid + "/" + "알림" + "/" + msg);

                            r.broadcast_room(protocol.getPacket());
                            //새로운 사용자를 알린다
                            //  r.BroadCast_Room("Chatting|" + roomid + "|알림| |" + userid + "입장했습니다");
                            //JDBC로 인서트
                            InsertTable(roomid, userid);
                            //사용자 추가
                            r.Add_User(this);

                        }
                    }
                    break;

                case Protocol.PT_CHAT:

          /*          //데이터를 무사히 잘 받았으면 클라이언트로 OK사인 보내기
                    Protocol checkProtocol = new Protocol(Protocol.PT_CHECK, 0);
                    checkProtocol.setProtocolType(String.valueOf(Protocol.PT_CHECK));
                    checkProtocol.setSocketCheck(String.valueOf(Protocol.SOCKET_CHECK));

                    send_packet(checkProtocol.getPacket());
                    //////*/

                    String msgtype = protocol.getMsgType();
                    String image = protocol.getUserimg();

                    if (msgtype.equals(protocol.Message)) { //일반메시지
                        String msg = protocol.getMsg();

                        String totalLen = protocol.getTotalLen().trim();
                        // int msgType = protocol.getMsgType();

                        protocol = new Protocol(Integer.parseInt(totalLen));
                        protocol.setProtocolType("2");
                        protocol.setTotalLen(totalLen);
                        //   protocol.setDataLength(msglength);
                        protocol.setMsgType(msgtype);
                        protocol.setRoomid(roomid);
                        protocol.setUserid(userid);
                        protocol.setUserimg(image);
                        protocol.setMsg(msg);

                        System.out.println(roomid + " | " + userid + "|" + image + " : " + msg);


                    } else if (msgtype.equals(protocol.IMG)) { // 이미지

                        String totalLen = protocol.getTotalLen().trim();

                        //이미지를 받아서 보내기...현재시간으로 파일이름 생성할까?
                        //   BufferedOutputStream bos = protocol.getChatImgs("http://13.124.77.49/uploads/tttt.jpg", dis);

                        byte[] data = protocol.getImg(Integer.parseInt(totalLen) - 114);

                        //getimg의 길이만큼
                        protocol = new Protocol(Integer.parseInt(totalLen));
                        protocol.setProtocolType("2");
                        protocol.setTotalLen(totalLen);
                        // protocol.setDataLength(imgLen);
                        protocol.setMsgType(msgtype);
                        protocol.setRoomid(roomid);
                        protocol.setUserid(userid);
                        protocol.setUserimg(image);
                        protocol.sendImage(data);

                        System.out.println(roomid + "|" + userid + "| 이미지 전체 크기 = " + totalLen);

                        long curr = System.currentTimeMillis();
                        byte[] temp = new byte[Integer.parseInt(totalLen) - 114];

                        System.arraycopy(protocol.getPacket(), 114, temp, 0, Integer.parseInt(totalLen) - 114);

                        //서버에 이미지 저장
                        try {
                            byte2Image("/usr/share/nginx/html/uploads/" + curr + ".jpg", temp);
                          //  byte2Image("/usr/share/nginx/html/uploads/" + curr + ".mp4", temp);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                    //나에게보내는 메시지...
                    //send_packet(protocol.getPacket());

                    //방안의 전체사용자에게
                    broadcast_Room(roomid, protocol.getPacket());


                    break;

                //소켓연결 체크용
                case Protocol.PT_CHECK:
                    int check = Integer.parseInt(protocol.getSocketCheck());
                    if (check == Protocol.SOCKET_CHECK) {
                        System.out.println("데이터잘받고 잘보냄");
                        String totalLen = protocol.getTotalLen().trim();
                        //데이터를 받았으면 그대로 보내주기
                        protocol = new Protocol(Integer.parseInt(totalLen));
                        protocol.setProtocolType(String.valueOf(Protocol.PT_CHECK));
                        protocol.setTotalLen(totalLen);
                        protocol.setSocketCheck(String.valueOf(Protocol.SOCKET_CHECK));
                    }
                    //나에게 소켓연결 상태 보냄
                    send_packet(protocol.getPacket());

                    break;

                case Protocol.PT_UNDEFINED:
                    break;
            }


        }


        public void byte2Image(String path, byte[] buffer) throws FileNotFoundException, IOException {
            FileOutputStream imageOutput = new FileOutputStream(new File(path));
            imageOutput.write(buffer, 0, buffer.length);
            imageOutput.close();
        }

    } //UserInfo class끝


    class RoomInfo {
        private String Room_name; //방이름
        // private Vector Room_user_vc = new Vector(); //방안에 있는 접속자목록
        private ArrayList RoomUserList = new ArrayList();
        private ArrayList OutingList = new ArrayList(); //강제퇴장당한 목록


        RoomInfo(String str, UserInfo u) {
            this.Room_name = str;
            this.RoomUserList.add(u);
        }

        public void broadcast_room(byte[] packet) {
            for (int i = 0; i < RoomUserList.size(); i++) {
                UserInfo u = (UserInfo) RoomUserList.get(i);
                u.send_packet(packet);
            }
        }

        private void Add_User(UserInfo u) {
            this.RoomUserList.add(u);
        } //기존 채팅방에 사용자 추가시켜주는 메소드
    }


    public void broadcast_Room(String roomid, byte[] packet) {
        for (int i = 0; i < roomList.size(); i++) {
            RoomInfo r = roomList.get(i);
            if (r.Room_name.equals(roomid)) {
                r.broadcast_room(packet);
            }
        }
    }


    public void InsertTable(String hostid, String userid) {

        Connection conn = null;
        Statement stmt = null;

        try {

            Class.forName("org.postgresql.Driver");

            conn = DriverManager.getConnection(
                    "jdbc:postgresql://13.124.77.49:5432/postgres", "postgres",
                    "alsdkek123!");

            stmt = conn.createStatement();

            String sql = "INSERT INTO roomuserlist (roomid, userid) VALUES ('" + hostid + "', '" + userid + "')";

            stmt.executeUpdate(sql);

            System.out.println(stmt);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


}



/*
//서버스탑시키기
server_socket.close();
user_vector.removeAllElements();
room_vertor.removeAllElements();*/



