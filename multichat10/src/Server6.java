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
public class Server6 {

    //네트워크 자원
    private ServerSocket serverSocket;
    private Socket socket;

    // private List<UserInfo> userList = new ArrayList<UserInfo>();

    StringTokenizer st;

    private List<RoomInfo> roomList = new ArrayList<RoomInfo>();

    private void Server_Start() {
        try {
            serverSocket = new ServerSocket(12345);  //12345번 포트 사용
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("이미 사용중인 포트입니다");
        }

        if (serverSocket != null) { //정상적으로 포트가 열렸을 경우

            System.out.println("서버 시작");

            //룸목록 불러와서 셋팅하기.

            //룸마다 사용자 셋팅!

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

                        //사용자가 접속하면 UserInfo객체를 만들어주는데, 사용자가 접속한 소켓정보를 넣어준다
                        UserInfo userInfo = new UserInfo(socket);
                        userInfo.start(); //개별 스레드 실행

                    } catch (IOException e) {
                     //서버에 문제!
                        e.printStackTrace();
                        System.out.println("accept 에러 발생");
                        break;
                    }
                }

            }
        });

        thread.start();


    }

    public static void main(String[] args) {

        Server6 server = new Server6();
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

        UserInfo(Socket socket, String userid) { //생성자 메소드, userid까지 입력받는 생성자
            this.user_socket = socket;
            this.userid = userid;
            UserNetwork();
        }

        private void UserNetwork() { //네트워크 자원설정
            try {
                is = user_socket.getInputStream();
                dis = new DataInputStream(is);

                os = user_socket.getOutputStream();
                dos = new DataOutputStream(os);
                System.out.println("클라이언트 접속" + user_socket.toString());

            } catch (IOException e) {
                //
                e.printStackTrace();
                System.out.println("Stream 설정 에러 발생");
            }

        }

        public void run() { //스레드로 처리할 내용. 클라이언트에서 들어오는 메시지 개별적으로 받음

            while (true) {
                try {
                    //while문 안에 넣어버리니까... IOExceptino을 잡지 못하네..
                    while (dis.available() > 0) {
                        System.out.println("dis.available 안에 있음");
                        String msg = dis.readUTF(); //메시지 받음

                        inmessage(msg);
                    }

                    // System.out.println(nickName + " : " + msg);
                } catch (IOException e) {
                    e.printStackTrace();
                    //클라이언트의 접속이 끊어졌을 때, 에러발생.
                    //사용자 로그아웃시에
                    System.out.println(userid + "사용자 접속끊어짐");
                    try {
                        System.out.println( "자원정리");
                        //그 사용자와 연결된 스트림 닫아줌
                        is.close();
                        os.close();
                        dos.close();
                        dis.close();
                        user_socket.close();

                    } catch (IOException e1) {
                    }
                    break;

                }
            }

        } //run메소드 끝

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
            String userid = st.nextToken();

            if (protocol.equals("CreateRoom")) { //createroom/roomid/userid

                RoomInfo new_room = new RoomInfo(Message, this);
                roomList.add(new_room); //전체방벡터에 추가
                Collections.synchronizedList(roomList); //roomlist동기화

                System.out.println("CreateRoom/" + Message + "성공");
                //연속적으로 서버에서 메시지가 가면.....읽어들이지 못하는 문제..scanner부분때문에
                // BroadCast("New_Room/" + Message); //다른 접속자들에게도 새로운 방이 만들어졌음을 알려준다

            } else if (protocol.equals("Chatting")) { //chatting/roomid/userid/userimg/msg
                String image = st.nextToken();
                String msg = st.nextToken();

                for (int i = 0; i < roomList.size(); i++) { //방찾기
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) {//해당방을 찾았을 때
                        r.BroadCast_Room("Chatting|" + Message + "|" + userid + "|" + image + "|" + msg); //내이름으로 다른 사람들에게 메시지 전달
                        System.out.println(Message + " | " + userid + " : " + msg);
                    }
                }
            } else if (protocol.equals("JoinRoom")) { //기존의 방 들어가기
                for (int i = 0; i < roomList.size(); i++) {
                    RoomInfo r = roomList.get(i);
                    if (r.Room_name.equals(Message)) {

                        //새로운 사용자를 알린다
                        r.BroadCast_Room("Chatting|" + Message + "|알림| |" + userid + "입장했습니다");

                        //JDBC로 인서트
                        InsertTable(Message, userid);

                        //사용자 추가
                        r.Add_User(this);

                     //   send_Message("JoinRoom|" + Message);//Message는 방이름

                    }
                }
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

            String sql = "INSERT INTO roomuserlist (roomid, userid) VALUES ('" + hostid + "', '" + userid + "')";

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
