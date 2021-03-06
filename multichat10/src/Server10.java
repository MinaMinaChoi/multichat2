
import redis.clients.jedis.Jedis;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.*;
import java.util.*;

/**
 * Created by cmina on 2017-07-05
 */
public class Server10 {

    //네트워크 자원
    private ServerSocket serverSocket;
    private Socket socket;

    //사용자가 접속할 때마다 userlist에 유저정보 저장
    private List<UserInfo> userList = new ArrayList<UserInfo>();

    //CreateRoom하면 roolist에 방정보 저장!
    private List<RoomInfo> roomList = new ArrayList<RoomInfo>();

    private void Server_Start() {
        try {
            serverSocket = new ServerSocket(12345);  //12345번 포트 사용
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("이미 사용중인 포트입니다");
        }

        if (serverSocket != null) { //정상적으로 포트가 열렸을 경우
            System.out.println("서버10 시작");
            //새로운 사용자받는 스레드 돌리기 전에!
            //비정상종료일 때,
            //다시 시작하면서
            //디비에서 방정보와 유저정보를 가지고 와서
            //소켓연결하도록....
            setRoomlist();

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
                while (true) {
                    try {
                        socket = serverSocket.accept(); //사용자 접속 대기 무한대기

                        //접속자리스트의 userid
                        for (int i = 0; i < userList.size(); i++) {
                            System.out.println("접속자리스트 아이디 확인 : " + userList.get(i).userid);
                        }

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

        Server10 server = new Server10();
        server.Server_Start();

    }

    //사용자 클래스
    class UserInfo extends Thread {

        private Socket user_socket;
        private String userid = ""; //채팅에 접속한 유저마다 개별 쓰레드. 개별적인 닉네임.

        private OutputStream os;
        private InputStream is;
        private DataOutputStream dos;
        private DataInputStream dis;

        UserInfo(Socket socket) { //생성자 메소드
            this.user_socket = socket;
            UserNetwork();
        }

        private void UserNetwork() { //네트워크 자원설정
            try {
                is = user_socket.getInputStream();
                dis = new DataInputStream(is);

                os = user_socket.getOutputStream();
                dos = new DataOutputStream(os);
                //    System.out.println("클라이언트 접속" + user_socket);

                String firstString = dis.readUTF();
                StringTokenizer st = new StringTokenizer(firstString, "|");

                userid = st.nextToken();
                String msgid = st.nextToken();
                // userid = dis.readUTF();
                System.out.println(userid + "님 접속/" + msgid + "/" + user_socket);

                //서버 비정상종료 후, 재가동시.
                //roomList 전체 반복,
                // roomid를 사용해서,
                //만약 해당룸 유저목록에 나의 아이디가 있다면 추가
                //서버 최초 가동시
                for (int i = 0; i < roomList.size(); i++) {
                    boolean ch = false;
                    RoomInfo roomInfo = (RoomInfo) roomList.get(i);

                    // 그리고 이미 그 방목록에 나의 아이디가 있다면...추가 안함
                    //어케 해야하지
                    if (setRoomUserlist(roomInfo.Room_name, userid)) {     //해당방 유저목록에 내 아이디가 있다면

                        for (int j = 0; j < roomInfo.roomuserlist.size(); j++) { //유저리스트를 다 돌았는데도 내 아이디가 없으면. 추가해주지.

                            String check = roomInfo.roomuserlist.get(j);

                            if (userid.equals(check)) { //만약 이미 방유저리스트에 내가 들어가있으면, true
                                ch = true; // 룸유저리스트에 내가 없다... 추가를 해주자.
                            }
                        }

                        if (roomInfo.roomuserlist.size() == 0 || !ch) { //아 이랬더니...두번째 클라에서, 추가가 안되는군...미리 들어온 유저때메 사이즈가 0이 아니니까,
                            //그리고, ch가 false이면 추가. 방목록에 내가 없기 때문에...
                            System.out.println(roomInfo.Room_name + "에 사용자 " + userid + "추가");
                            roomInfo.add_user(userid);
                        }


                        //그 방에.... 내가 받지 못한 메시지가 있다면 보내주도록..
                        //클라이언트의 최신 메시지의 msgid값과
                        //해당방의 msgid를 비교해서,
                        if (!msgid.equals("")) {
                            CheckRecentMsg(this, msgid);
                        }
                    }
                }

                //서버 최초 가동시 통과
                for (int i = 0; i < userList.size(); i++) {
                    if (userid.equals(userList.get(i).userid)) {
                        // check = true;
                       /* UserInfo u = userList.get(i);
                        u.user_socket = user_socket;*/
                        //기존에 있던 것 리스트에서 지우고
                        userList.remove(i);
                        System.out.println("이미 접속했던 사용자 : " + userid);
                    }
                }

                userList.add(this);
                Collections.synchronizedList(userList);
                System.out.println("최초 접속 사용자 : " + userid);

                System.out.println("현재 접속된 사용자 수 : " + userList.size());

            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
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

                    //인풋스트림에서 읽을 게 있는 만큼.....
                    protocol = new Protocol(is.available());
                    buf = protocol.getPacket();
                    System.out.println("left 0일 떄, 인풋스트림에서 읽어온길이 : " + buf.length);

                    is.read(buf);

                    int total_len = Integer.parseInt(protocol.getTotalLen()); //받아야할 길이

                    int rec_len = buf.length; //받은길이
                    int rec_cnt = 0;

                    int protocol_type = Integer.parseInt(protocol.getProtocolType().trim()); //프로토콜 타입
                    String roomid = protocol.getRoomid().trim();
                    String userid = protocol.getUserid().trim();


                    if (protocol_type == Protocol.PT_CHAT_MOVIE) { //바로바로 파일에 쓰기시작!
                        System.out.println("동영상파일 전송받는중. 바로 임시파일에 쓰기");
                        //읽은 바이트배열중에, 헤더부분 233 빼고

                        String uesrimg = protocol.getUserimg().trim();
                        String filename = protocol.getFileName().trim();
                        String tempfile = "/usr/share/nginx/html/temp/" + filename + ".tempfile";

                        File file = new File(tempfile);
                        System.out.println(tempfile);

                        if (file.isFile()) { //해당파일 이미 존재~

                            System.out.println("해당파일 이미 존재");
                            //이어받기!!!
                            FileOutputStream fos = new FileOutputStream(file, true);

                            fos.write(buf, 233, buf.length - 233);

                            while (total_len > rec_len) { //읽은 바이트길이보다 최종길이가 더 길면...
                                //더받아서 파일이어쓰기를 해줘야하지.
                                if (is.available() > 0) {

                                    buf = new byte[is.available()];
                                    System.out.println(" 계속 읽는 중" + is.available());

                                    rec_cnt = is.read(buf); //inputstream에서 읽어와서 buf바이트배열에 담는다
                                    System.out.println("더 읽은 데이터 길이 : " + rec_cnt);

                                    if ((total_len - rec_len) >= rec_cnt) {
                                        //  protocol.addPacket(rec_len, buf);
                                        fos.write(buf, 0, buf.length);
                                        rec_len += rec_cnt; //읽은 데이터 길이를 더한다.

                                    } else {
                                        // protocol.addPacket2(total_len, rec_len, buf);
                                        fos.write(buf, 0, total_len - rec_len);
                                        rec_len += (total_len - rec_len); //읽은 데이터 길이를 더한다.
                                        left_packet = rec_cnt - (total_len - rec_len);
                                    }

                                    System.out.println("지금까지 읽은 데이터 길이 : " + rec_len);

                                }
                            }

                            fos.close();

                        } else {
                            FileOutputStream fos = new FileOutputStream(file, true);

                            System.out.println("버퍼의 크기 : " + buf.length + " totalLength : " + total_len);
                            //233 이전에는 헤더부분 실제데이터는 233부터
                            fos.write(buf, 233, buf.length - 233);

                            while (total_len > rec_len) { //읽은 바이트길이보다 최종길이가 더 길면...
                                //더받아서 파일이어쓰기를 해줘야하지.
                                if (is.available() > 0) {

                                    buf = new byte[is.available()];

                                    //  System.out.println("left 0일 떄, 계속 읽는 중" + is.available());

                                    rec_cnt = is.read(buf); //inputstream에서 읽어와서 buf바이트배열에 담는다
                                    //     System.out.println("left 0일 떄, 더 읽은 데이터 길이 : " + rec_cnt);

                                    if ((total_len - rec_len) >= rec_cnt) {
                                        //  protocol.addPacket(rec_len, buf);
                                        fos.write(buf, 0, rec_cnt);
                                        rec_len += rec_cnt; //읽은 데이터 길이를 더한다.

                                    } else {
                                        // protocol.addPacket2(total_len, rec_len, buf);
                                        fos.write(buf, 0, total_len - rec_len);
                                        rec_len += (total_len - rec_len); //읽은 데이터 길이를 더한다.
                                        left_packet = rec_cnt - (total_len - rec_len);
                                    }

                                    //   System.out.println("지금까지 읽은 데이터 길이 : " + rec_len);

                                }
                            }

                            fos.close();
                        }

                        //다 읽었으면//tempfile 확장자 떼기
                        ///usr/share/nginx/html/uploads/
                        long curr = System.currentTimeMillis();
                        String filepath = "/usr/share/nginx/html/uploads/" + curr + ".mp4";
                        File fileToMove = new File(filepath);
                        boolean isMoved = file.renameTo(fileToMove);

                        if (isMoved) {
                            System.out.println("파일이동 성공" + filepath);
                        } else {
                            System.out.println("파일이동 실패");
                        }

                        //파일 이동한 후에, 레디스에 입력해주기
                        //서버의 파일을 읽어서 클라이언트로 보내준다.
                        //File oFile = new File(path);
                        long lFileSize = fileToMove.length();
                        int filesize = (int) (long) lFileSize;

                        System.out.println("동영상 파일크기" + lFileSize);

                        //바로 보내지 말고? 체크?? 이어받기 할 것인지 말것인지??
                        Protocol protocol2 = new Protocol(filesize + 233); //채팅프로토콜+파일사이즈 만큼의 바이트배열을 만든다!
                        protocol2.setTotalLen(String.valueOf(filesize + 233));
                        protocol2.setProtocolType(String.valueOf(Protocol.PT_CHAT_MOVIE));
                        protocol2.setRoomid(roomid);
                        protocol2.setUserid(userid);
                        protocol2.setUserimg(uesrimg);
                        protocol2.setFileName(filepath.replace("/usr/share/nginx/html/uploads/", ""));
                        protocol2.setMsgId("" + curr);
                        protocol2.sendVideo(filepath);


                        //redis에 메시지 저장
                        ChatMessage chatMessage = new ChatMessage();
                        chatMessage.setRoomid(roomid);
                        chatMessage.setUserid(userid);
                        chatMessage.setMsgtype("" + 2);
                        chatMessage.setMsg(filepath);

                        insertRedis(chatMessage, curr);

                        //방안의 전체사용자에게
                        broadcast_Room(roomid, protocol2.getPacket());

                    } else {

                        //원래받던 방식대로.
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

                        //패킷타입을 얻고 protocol객체의 packet 멤버변수에 buf를 복사.
                        int packetType = Integer.parseInt(protocol.getProtocolType());
                        //전체크기만큼 다 받은 후에, 레디스에 입력(일반 텍스트의 경우)
                        inmessage(packetType, protocol);

                    }

                    ///////////////////////
                } catch (IOException e) {
                    e.printStackTrace();
                    //클라이언트의 접속이 끊어졌을 때, 에러발생.
                    System.out.println(userid + "사용자 접속끊어짐");

                    try {
                        //그 사용자와 연결된 스트림 닫아줌
                        dos.close();
                        dis.close();
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
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println(userid + "사용자 접속끊어짐");

                try {
                    is.close();
                    os.close();
                    dos.close();
                    dis.close();
                    user_socket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                //user_vector.remove(this);
                userList.remove(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void inmessage(int protocoltype, Protocol protocol) {

            String roomid = protocol.getRoomid();
            String userid = protocol.getUserid();

            ChatMessage chatMessage;
            long curr = System.currentTimeMillis();

            switch (protocoltype) {
                //프로토콜 타입 정의 다시1!!
                case Protocol.PT_CREATEROOM:

                    if (!roomid.equals("")) {
                        RoomInfo new_room = new RoomInfo(roomid, userid);
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

                            String msg = userid + "입장했습니다";

                            protocol = new Protocol(msg.getBytes().length + 133);
                            protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_MSG));
                            protocol.setTotalLen(String.valueOf(msg.trim().getBytes().length + 133));
                            protocol.setRoomid(roomid);
                            protocol.setUserid("알림");
                            protocol.setUserimg("");
                            protocol.setMsgId("" + curr);
                            protocol.setMsg(msg);

                            System.out.println(msg.trim().getBytes().length + "/" + roomid + "/" + "알림" + "/" + msg);

                            r.sendToRoomuser(protocol.getPacket());

                            //redis에 메시지 저장
                            chatMessage = new ChatMessage();
                            chatMessage.setRoomid(roomid);
                            chatMessage.setUserid("알림");
                            chatMessage.setMsgtype("" + 0);
                            chatMessage.setMsg(msg);

                            insertRedis(chatMessage, curr);

                            //JDBC로 인서트
                            InsertTable(roomid, userid);
                            //사용자 추가
                            r.add_user(userid);

                        }
                    }
                    break;

                //일반메시지일 때,
                case Protocol.PT_CHAT_MSG:

                    String image = protocol.getUserimg();
                    String msg = protocol.getMsg();
                    String totalLen = protocol.getTotalLen().trim();

                    protocol = new Protocol(Integer.parseInt(totalLen));
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_MSG));
                    protocol.setTotalLen(totalLen);
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    protocol.setUserimg(image);
                    protocol.setMsgId("" + curr);
                    protocol.setMsg(msg);

                    //redis에 메시지 저장
                    chatMessage = new ChatMessage();
                    chatMessage.setRoomid(roomid);
                    chatMessage.setUserid(userid);
                    chatMessage.setMsgtype("" + 0);
                    chatMessage.setMsg(msg);

                    insertRedis(chatMessage, curr);

                    System.out.println(roomid + " | " + userid + "|" + image + " : " + msg);

                    //방안의 전체사용자에게
                    broadcast_Room(roomid, protocol.getPacket());


                    break;

                //이미지 메시지일 때
                case Protocol.PT_CHAT_IMG:

                    String image1 = protocol.getUserimg();

                    //if (msgtype.equals(Protocol.IMG)) { // 이미지

                    String totalLen1 = protocol.getTotalLen().trim();

                    //이미지를 받아서 보내기...현재시간으로 파일이름 생성할까?
                    //   BufferedOutputStream bos = protocol.getChatImgs("http://13.124.77.49/uploads/tttt.jpg", dis);

                    byte[] data = protocol.getImg(Integer.parseInt(totalLen1) - 133);

                    //getimg의 길이만큼
                    protocol = new Protocol(Integer.parseInt(totalLen1));
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_IMG));
                    protocol.setTotalLen(totalLen1);
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    protocol.setUserimg(image1);
                    protocol.setMsgId("" + curr);
                    protocol.sendImage(data);

                    System.out.println(roomid + "|" + userid + "| 이미지 전체 크기 = " + totalLen1);

                    byte[] temp = new byte[Integer.parseInt(totalLen1) - 133];

                    System.arraycopy(protocol.getPacket(), 133, temp, 0, Integer.parseInt(totalLen1) - 133);

                    //서버에 이미지 저장
                    try {
                        byte2Image("/usr/share/nginx/html/uploads/" + curr + ".jpg", temp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    //redis에 메시지 저장
                    chatMessage = new ChatMessage();
                    chatMessage.setRoomid(roomid);
                    chatMessage.setUserid(userid);
                    chatMessage.setMsgtype("" + 1);
                    chatMessage.setMsg("/usr/share/nginx/html/uploads/" + curr + ".jpg");

                    insertRedis(chatMessage, curr);

                    //방안의 전체사용자에게
                    broadcast_Room(roomid, protocol.getPacket());

                    break;

                case Protocol.PT_CHAT_MOVIE:

                    String image2 = protocol.getUserimg();

                    //if (msgtype.equals(Protocol.IMG)) { // 이미지

                    String totalLen2 = protocol.getTotalLen().trim();
                    String fileName = protocol.getFileName().trim();

                    //이미지를 받아서 보내기...현재시간으로 파일이름 생성할까?
                    //   BufferedOutputStream bos = protocol.getChatImgs("http://13.124.77.49/uploads/tttt.jpg", dis);

                    byte[] data2 = protocol.getVideo(Integer.parseInt(totalLen2) - 233);

                    //getimg의 길이만큼
                    protocol = new Protocol(Integer.parseInt(totalLen2));
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_MOVIE));
                    protocol.setTotalLen(totalLen2);
                    protocol.setFileName(fileName);
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    protocol.setUserimg(image2);
                    protocol.setMsgId("" + curr);
                    protocol.sendVideo(data2);

                    System.out.println(roomid + "|" + userid + fileName + "| 동영상 전체 크기 = " + totalLen2);

                    //서버에 동영상 저장
                    //  long curr2 = System.currentTimeMillis();
                    byte[] temp2 = new byte[Integer.parseInt(totalLen2) - 233];

                    System.arraycopy(protocol.getPacket(), 233, temp2, 0, Integer.parseInt(totalLen2) - 233);

                    try {
                        byte2Image("/usr/share/nginx/html/uploads/" + curr + ".mp4", temp2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //방안의 전체사용자에게
                    broadcast_Room(roomid, protocol.getPacket());

                    break;

                //이미 tempfile이 존재하는 지 확인.
                case Protocol.PT_OFFSET:

                    String filename = protocol.getFileName();

                    String tempfile = "/usr/share/nginx/html/temp/" + filename + ".tempfile";

                    File file = new File(tempfile);
                    System.out.println(tempfile);

                    Protocol protocol1 = new Protocol(233);
                    protocol1.setTotalLen(String.valueOf(233));
                    protocol1.setProtocolType(String.valueOf(Protocol.PT_OFFSET));

                    if (file.isFile()) {
                        //tempfile의 크기를 보내기
                        long lFileSize = file.length();

                        int filesize = (int) lFileSize;
                        System.out.println("템프파일 이미 존재" + lFileSize + "/" + filesize);
                        protocol1.setOffSet("" + filesize);

                    } else {
                        //offset 0으로 보내기
                        protocol1.setOffSet(String.valueOf(0));
                        System.out.println("템프파일 없음");
                    }

                    send_packet(protocol1.getPacket());

                    break;

                //소켓연결 체크용
                case Protocol.PT_CHECK:
                    int check = Integer.parseInt(protocol.getSocketCheck());
                    if (check == Protocol.SOCKET_CHECK) {
                        System.out.println("데이터잘받고 잘보냄");
                        String totalLen3 = protocol.getTotalLen().trim();
                        //데이터를 받았으면 그대로 보내주기
                        protocol = new Protocol(Integer.parseInt(totalLen3));
                        protocol.setProtocolType(String.valueOf(Protocol.PT_CHECK));
                        protocol.setTotalLen(totalLen3);
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
        //방안에 있는 접속자목록
        private ArrayList RoomUserList = new ArrayList();

        //방안의 유저리스트 수정
        private ArrayList<String> roomuserlist = new ArrayList<>();

/*
        RoomInfo(String str, UserInfo u) {
            this.Room_name = str;
            this.RoomUserList.add(u);
        }
*/

        //생성자 수정
        RoomInfo(String roomid, String userid) {
            this.Room_name = roomid;
            this.roomuserlist.add(userid);
        }

        RoomInfo(String roomid) {
            Room_name = roomid;
        }

        //방안의 유저들에게 메시지 보내는 메소드
        public void sendToRoomuser(byte[] packet) {
            for (int i = 0; i < roomuserlist.size(); i++) {

                String userid = roomuserlist.get(i);
                // System.out.println("방유저리스트" + userid);
                //
                //서버에 접속중인 userList중에서 해당 userid의 소켓을 통해서 보내기
                for (int j = 0; j < userList.size(); j++) { //여기서 두번.
                    UserInfo userInfo = (UserInfo) userList.get(j);

                    if (userid.equals(userInfo.userid)) {
                        System.out.println(userInfo.userid + "소켓 확인 : " + userInfo.user_socket);
                        userInfo.send_packet(packet); //여기서 에러....
                    }

                }

            }
        }

        private void add_user(String userid) {
            this.roomuserlist.add(userid);
        }
    }


    public void broadcast_Room(String roomid, byte[] packet) {
        for (int i = 0; i < roomList.size(); i++) {
            RoomInfo r = roomList.get(i);
            if (r.Room_name.equals(roomid)) {
                r.sendToRoomuser(packet);
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
        } finally {
            //finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            } catch (SQLException se) {
            }// do nothing
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }//end finally try
        }//end try
    }


    //서버비정상종료 후, 재가동시 방목록 가져오기
    public void setRoomlist() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet resultSet;

        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://13.124.77.49:5432/postgres", "postgres",
                    "alsdkek123!");
            stmt = conn.createStatement();
            //중복값 제거 위해서 DISTINCT 추가
            String sql = "SELECT DISTINCT roomid FROM roomuserlist";
            resultSet = stmt.executeQuery(sql);

            while (resultSet.next()) {

                String roomid = String.valueOf(resultSet.getInt("roomid"));

                RoomInfo roomInfo = new RoomInfo(roomid);
                roomList.add(roomInfo);
                // roomidList.add(roomid);
                System.out.println("서버 재가동 방목록에 추가 : " + roomid);
            }

            resultSet.close();

            System.out.println(stmt);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            //finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            } catch (SQLException se) {
            }// do nothing
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }//end finally try
        }//end try
    }


    //서버비정상종료후, 재가동시 유저목록 가져오기
    public boolean setRoomUserlist(String roomid, String userid) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet resultSet;
        boolean ret = false;

        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://13.124.77.49:5432/postgres", "postgres",
                    "alsdkek123!");
            stmt = conn.createStatement();
            String sql = "SELECT userid FROM roomuserlist WHERE roomid='" + roomid + "' AND userid='" + userid + "'";
            resultSet = stmt.executeQuery(sql);

            while (resultSet.next()) {
                ret = true;
            }
            resultSet.close();
            //   System.out.println(stmt);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            //finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            } catch (SQLException se) {
            }// do nothing
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }//end finally try
        }//end try

        //System.out.println("결과" + ret);
        return ret;
    }


    public void insertRedis(ChatMessage chatMessage, long curr) {
        //Connecting to Redis server on localhost
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        jedis.auth("alsdkek123!");
        System.out.println("Connection to server sucessfully");

        Map<String, String> chatMsg = new HashMap<String, String>();
        chatMsg.put("roomid", chatMessage.getRoomid());
        chatMsg.put("userid", chatMessage.getUserid());
        chatMsg.put("msgtype", chatMessage.getMsgtype());
        chatMsg.put("msg", chatMessage.getMsg());

        jedis.hmset("msgid:" + curr, chatMsg);

        if (jedis != null)
            jedis.close();

    }

    class RecentCompare implements Comparator<String> {
        /**
         * 오름차순(ASC)
         */
        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
            //o1가 더 크면 true
        }
    }


    public void CheckRecentMsg(UserInfo userInfo, String msgid) {
        Protocol protocol = null;
        //Connecting to Redis server on localhost
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        jedis.auth("alsdkek123!");
        //System.out.println("Connection to server sucessfully");

        List<String> list = new ArrayList<String>(jedis.keys("msgid:*"));
        //오름차순으로 정렬
        Collections.sort(list, new RecentCompare());
        //순서를 바꿔줘야함..
        //최근 입력된 것 아니라,
        //받지못한 메시지중에서 제일 이전의 것을 가져와야.

        for (int i = 0; i < list.size(); i++) {

            String MSGID = list.get(i).toString().replace("msgid:", "");

            if (MSGID.compareTo(msgid) > 0) { //받지 못한 메시지가 있으면 전체 필드값을 가져와서 보내주기.
                //전체 필드 값 다 가져오기...
                System.out.println("서버 저장된 메시지아이디 " + list.get(i).toString());
                System.out.println("클라 저장된 메시지아이디 " + msgid);

                //msgid:1231313123

                List<String> fieldList = jedis.hmget(list.get(i), "roomid", "userid", "msg", "msgtype");

                String roomid = fieldList.get(0);
                String userid = fieldList.get(1);
                String msg = fieldList.get(2);
                String msgtype = fieldList.get(3);

                if (msgtype.equals("0")) { //일반메시지, 알림메시지....

                    protocol = new Protocol(133 + msg.getBytes().length);
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_MSG));
                    protocol.setTotalLen(String.valueOf(133 + msg.getBytes().length));
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    if (!userid.equals("알림")) {
                        protocol.setUserimg("http://13.124.77.49/thumbnail/" + userid + ".jpg");
                    }
                    protocol.setMsgId(MSGID);
                    protocol.setMsg(msg);

                } else if (msgtype.equals("1")) { //이미지

                    File file = new File(msg);
                    int filesize = 0;
                    if (file.isFile()) {
                        long lFileSize = file.length();
                        filesize = (int) (long) lFileSize;
                    }

                    protocol = new Protocol(133 + filesize);
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_IMG));
                    protocol.setTotalLen(String.valueOf(133 + filesize));
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    protocol.setUserimg("http://13.124.77.49/thumbnail/" + userid + ".jpg");
                    protocol.setMsgId(MSGID);
                    protocol.sendImage(msg);

                } else if (msgtype.equals("2")) { //동영상

                    File file = new File(msg);
                    int filesize = 0;
                    if (file.isFile()) {
                        long lFileSize = file.length();
                        filesize = (int) (long) lFileSize;
                    }

                    protocol = new Protocol(233 + filesize);
                    protocol.setProtocolType(String.valueOf(Protocol.PT_CHAT_MOVIE));
                    protocol.setTotalLen(String.valueOf(233 + filesize));
                    protocol.setRoomid(roomid);
                    protocol.setUserid(userid);
                    protocol.setUserimg("http://13.124.77.49/thumbnail/" + userid + ".jpg");
                    protocol.setMsgId(MSGID);
                    protocol.setFileName(msg.replace("/usr/share/nginx/html/uploads/", ""));
                    protocol.sendVideo(msg);

                }

                userInfo.send_packet(protocol.getPacket());
                System.out.println(MSGID +"/"+ protocol.getTotalLen() +" redis 받지못한 메시지 보냄");

            }
        }

        if (jedis != null)
            jedis.close();

    }
}

