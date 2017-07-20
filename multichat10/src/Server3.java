import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by cmina on 2017-05-21.
 */
public class Server3 {

    //네트워크 자원
    private ServerSocket serverSocket;
    private Socket socket;

    //사용자가 접속할 때마다 벡터에 유저정보 저장
    //private static Vector user_vector = new Vector();

    private List<UserInfo> userList = new ArrayList<UserInfo>();

    StringTokenizer st;

    //private static Vector room_vector = new Vector();
    private List<RoomInfo> roomList = new ArrayList<RoomInfo>();

    private boolean RoomCh = true; //방을 만들 수 있는 상태

    private boolean JoinCh = true; //방에 입장할 수 있는 상태.

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
                System.out.println("서버 시작");

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

        Server3 server = new Server3();
        server.Server_Start();
        //Server.java실행하면 바로 서버 스타트
        //  Server_Start(); //소켓 생성 및 사용자 접속 대기
        //이렇게 하니까....모든 메소드, 변수를 static으로 해야하네....main함수가 static이어서

    }

    //사용자 클래스
    class UserInfo extends Thread {

        private Socket user_socket;
        private String nickName = ""; //채팅에 접속한 유저마다 개별 쓰레드. 개별적인 닉네임.
        private Boolean RoomLeader = false;

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
                System.out.println("클라이언트 접속");

                //클라이언트가 처음 접속할 때, 아이디를 보내주기때문에, 아이디를 받아줘야함.
          /*      nickName = dis.readUTF(); //사용자의 닉네임 받음.
                System.out.println(nickName + "님 접속/" + user_socket);*/

                //broadcast
                //  BroadCast("NewUser/" + nickName);//현재 접속된 사용자에게 새로운 사용자알림

/*
                //자신에게 기존사용자를 받아오는 부분
                for (int i = 0; i < userList.size(); i++) {
                    UserInfo u = userList.get(i);
                    send_Message("OldUser/" + u.nickName);//나자신에게, user_vector에 포함된 사용자의 닉네임을 보내준다.
                }
*/

                //자신에게 기존 방 목록을 받아오는 부분
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = (RoomInfo) roomList.get(i);
                    send_Message("OldRoom|" + r.Room_name);
                }

                // user_vector.add(this);//사용자에게 알린후에 유저벡터에 자신을 추가, this는 userinfo자신.
                //서버에서 관리하는 참여자목록userlist에 추가
                /*userList.add(this);
                Collections.synchronizedList(userList);
                //list를 사용한다면.....synchronized해줘야....
                System.out.println("현재 접속된 사용자 수 : " + userList.size());*/

            } catch (IOException e) {
                //e.printStackTrace();
                System.out.println("Stream 설정 에러 발생");
            }

        }

        public void run() { //스레드로 처리할 내용. 클라이언트에서 들어오는 메시지 개별적으로 받음

            while (true) {
                try {
                    String msg = dis.readUTF(); //메시지 받음
                    inmessage(msg);
                    // System.out.println(nickName + " : " + msg);
                } catch (IOException e) {
                    //e.printStackTrace();
                    //클라이언트의 접속이 끊어졌을 때, 에러발생.
                    System.out.println(nickName + "사용자 접속끊어짐");

                    try {
                        //그 사용자와 연결된 스트림 닫아줌
                        dos.close();
                        dis.close();
                        user_socket.close();
                        //user_vector.remove(this);
                        userList.remove(this);
                        BroadCast("User_out|" + nickName);

                    } catch (IOException e1) {
                    }

                    System.out.println("현재 접속된 사용자 수 : " + userList.size());
                    break;

                }
            }

        } //run메소드 끝

        private void BroadCast(String str) { //전체사용자에게 메세지를 보내는부분
            for (int i = 0; i < userList.size(); i++) {
                UserInfo user = userList.get(i);
                System.out.println("브로드캐스트 텍스트확인" + str);
                user.send_Message(str);

            }
        }

        private void BroadCast(String str, Socket socket) { //나빼고 전체사용자에게
            for (int i = 0; i < userList.size(); i++) {
                UserInfo user = userList.get(i);
                if (user.user_socket != socket) {
                    System.out.println("브로드캐스트 텍스트확인" + str);
                    user.send_Message(str);

                }

            }
        }

        private void send_Message(String str) { //문자열을 받아서 전송
            try {
                dos.writeUTF(str);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        private void inmessage(String str) { //클라이언트로부터 들어오는 메시지 처리
            st = new StringTokenizer(str, "|");

            String protocol = st.nextToken();
            String Message = st.nextToken();

//            System.out.println("프로토콜 : "+protocol);
            //          System.out.println("내용 : "+Message);

            if (protocol.equals("CreateRoom")) {
                //1.현재같은 방이 존재하는지 확인
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) { //만들고자 하는 방이 이미 존재
                        send_Message("CreateRoomFail|ok");
                        System.out.println("CreateRoom|" + Message + "실패");
                        RoomCh = false;
                        break;
                    }
                } //for 끝

                if (RoomCh) {//방을 만들 수 있을때
                    RoomInfo new_room = new RoomInfo(Message, this);
                    roomList.add(new_room); //전체방벡터에 추가
                    Collections.synchronizedList(roomList); //roomlist동기화

                    send_Message("CreateRoom|" + Message); //사용자에게 방이 만들어졌음을 알려준다
                    BroadCast("New_Room|"+Message, user_socket);
                    RoomLeader = true;

                    System.out.println("CreateRoom/" + Message + "성공");
                    //연속적으로 서버에서 메시지가 가면.....읽어들이지 못하는 문제..scanner부분때문에
                    // BroadCast("New_Room/" + Message); //다른 접속자들에게도 새로운 방이 만들어졌음을 알려준다
                }

                RoomCh = true;

            } else if (protocol.equals("Chatting")) {
                String image =st.nextToken();
                String msg = st.nextToken(); //채팅내용, Chatting/방이름/userimage/채팅내용

                for (int i = 0; i < roomList.size(); i++) { //방찾기
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) {//해당방을 찾았을 때
                        r.BroadCast_Room("Chatting|"+ Message+"|" + nickName + "|" +image+"|" +msg); //내이름으로 다른 사람들에게 메시지 전달
                        System.out.println(Message + " | " + nickName + " : " + msg);

                    }
                }
            } else if (protocol.equals("JoinRoom")) { //기존의 방 들어가기
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) {

                        for (int j = 0; j < r.OutingList.size(); j++) { //해당방의 강제퇴장목록을 확인
                            String name = (String) r.OutingList.get(i);
                            if (nickName.equals(name)) {
                                //이미 퇴장 당한 방이어서, 입장 불가능
                                JoinCh = false;
                                System.out.println(nickName + "님 " + Message + "방에 입장불가능");
                                send_Message("JoinRoomFail|ok");
                                break;
                            }
                        } //for 끝

                        if (JoinCh) { //방에 입장할 수 있는 상태이면... 해당방에 강제퇴장적이 없으면
                            //새로운 사용자를 알린다
                            r.BroadCast_Room("Chatting|" +Message+"|알림| |" + nickName + "입장했습니다");

                            //JDBC로 인서트

                            InsertTable(Message, nickName);
                            //사용자 추가
                            r.Add_User(this);
                            send_Message("JoinRoom|" + Message);//Message는 방이름
                        }

                        JoinCh = true; //다시 방에 들어갈 수 있는 상태로 돌려놓는다

                    }
                }
            } else if (protocol.equals("ExitRoom")) { //방나가기
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) {
                        r.RoomUserList.remove(this);
                        Collections.synchronizedList(r.RoomUserList);
                        r.BroadCast_Room("ExitRoom|" + nickName);

                        if (r.RoomUserList.size() == 0) { //해당 채팅방에 아무도 없으면
                            roomList.remove(r);
                            Collections.synchronizedList(roomList);
                            System.out.println(r.Room_name + "방지움");
                        }
                    }
                }
            } else if (protocol.equals("OutByForce")) { //강제퇴장
                String msg = st.nextToken(); //UserID, OutByForce/방이름/UserID
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) { //해당방에서 강제퇴장ID지움
                        for (int k = 0; k < r.RoomUserList.size(); k++) {
                            UserInfo userInfo = (UserInfo) r.RoomUserList.get(k);
                            if (userInfo.nickName.equals(msg)) {
                                //해당 팀원에게 강퇴당했음을 알려주고,
                                //다른 팀원에게는 해당팀원이 강퇴됐음을 알려준다..
                                r.sendMessageToUser("Outing|" + Message, msg);
                                r.RoomUserList.remove(k);
                                Collections.synchronizedList(r.RoomUserList);
                                r.BroadCast_Room("OutByForce|" + Message + "|" + msg);
                            }
                        }
                        r.OutingList.add(msg); //방의 강제퇴장목록에 추가
                        Collections.synchronizedList(r.OutingList);

                        System.out.println(Message + "방에서 " + msg + " 강퇴당함");
                        break;
                    } else {
                        send_Message("dd|유효하지 않은 참여자입니다");
                    }
                }
            } else if (protocol.equals("UserEnter")) { //user가 소켓에 처음 접속했을 때,
                //try {
                //nickName = dis.readUTF(); //사용자의 닉네임 받음.
                nickName = Message;
                System.out.println(nickName + "님 접속|" + user_socket);

                userList.add(this);
                Collections.synchronizedList(userList);
                //list를 사용한다면.....synchronized해줘야....
                System.out.println("현재 접속된 사용자 수 : " + userList.size());

            } else {
                //형식에 맞지 않을경우.
                send_Message("Error|error");
            }
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

        public void BroadCast_Room(String str) {//현재 방에 속한 유저들을 하나씩 꺼내서, 채팅내용을 보내준다
            for (int i = 0; i < RoomUserList.size(); i++) {
                UserInfo u = (UserInfo) RoomUserList.get(i);
                    u.send_Message(str);
            }
        }

        public void BroadCast_Room(String str, Socket userSocket) {//현재 방에 속한 유저들을 하나씩 꺼내서, 채팅내용을 보내준다
            for (int i = 0; i < RoomUserList.size(); i++) {
                UserInfo u = (UserInfo) RoomUserList.get(i);
                 if (u.user_socket != userSocket) { //내가보낸 채팅내용은 안보여준다.
                u.send_Message(str);
                 }

            }
        }

        public void sendMessageToUser(String str, String userId) {
            for (int i = 0; i < RoomUserList.size(); i++) {
                UserInfo u = (UserInfo) RoomUserList.get(i);
                if (u.nickName.equals(userId)) { //특정 id에게만 보낸다
                    u.send_Message(str);
                    System.out.println(u.nickName + "에게 강퇴메시지");
                }
            }
        }

        private void Add_User(UserInfo u) {
            this.RoomUserList.add(u);
        } //기존 채팅방에 사용자 추가시켜주는 메소드
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

            String sql = "INSERT INTO roomuserlist (roomid, userid) VALUES ('"+hostid+"', '"+userid+"')";

            stmt.executeUpdate(sql);

            System.out.print(stmt);

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
