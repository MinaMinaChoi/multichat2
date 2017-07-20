import java.io.*;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by cmina on 2017-05-21.
 */
public class Client {

    //네트워크 자원 변수
    private Socket socket;
    private String ip = "52.78.207.202";
    private int port = 12345;
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String id;
    private boolean inRoom = false;
    private boolean RoomLeader = false;

    //그외 변수들
    //클라이언트에서 user_list를 알아야 하나???
    //내가 참여한 roomList...
    //  static ArrayList userList = new ArrayList();
    ArrayList roomList = new ArrayList();

    StringTokenizer st;

    private String My_Room; //현재 내가 있는 방이름

    private void Network() {
        try {
            socket = new Socket(ip, port);

            if (socket != null) { //정상적으로 소켓이 연결되었으면.
                //실제적인 연결
                is = socket.getInputStream();
                dis = new DataInputStream(is);

                os = socket.getOutputStream();
                dos = new DataOutputStream(os);
            }

        } catch (UnknownHostException e) {
            System.out.println("연결실패");
            // e.printStackTrace();
        } catch (IOException e) {
            System.out.println("연결실패");
            // e.printStackTrace();
        }

        System.out.println("사용할 아이디를 입력하세요");
        Scanner scanner = new Scanner(System.in);

        id = scanner.nextLine();

        //처음 접속 시에 아이디 서버에 전송
        send_message(id);

        //유저리스트에 사용자 추가
        // userList.add(id);

        Thread receiverThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {

                    try {

                        //연속적으로 서버로부터 메시지를 받을 때,,,,, scanner때문에, 엔터가 없으면 안넘어가네...
                        //메시지 받는 쓰레드와, 메시지 보내는 쓰레드를 분리해서 돌려야겠네...
                        String msg = dis.readUTF(); //메시지 받음

                        if (msg != null) { //서버로부터 받은 메시지가 있을 때만, 해석.
                          //  System.out.println("서버로부터 받은 메시지 :" + msg);
                            inmessage(msg);
                        }

                    } catch (IOException e) {
                        //e.printStackTrace();
                        //서버와 끊어졌을 때, 사용한 자원들은 닫아줌
                        try {
                            os.close();
                            is.close();
                            dos.close();
                            dis.close();
                            socket.close();
                            System.out.println("서버와 접속 끊어짐");
                        } catch (IOException ex) {

                        }

                        break; //현재돌아가는 while문 정지

                    }
                }

            }
        });

        receiverThread.start();

        Thread sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //입력받은 메시지 서버에 보내기

                while (true) {
                    String message = scanner.nextLine();

                    if (message != null) {

                        //채팅방 안에 있느냐, 밖에 있느냐에 따라, 사용할 수 있는 명령어를 구분해야하는데.
                        //어떻게...해주는게 낫지

                        try {
                            if (inRoom) {

                                //채팅방안에 있을 때,
                                //ExitRoom/방이름
                                //Back/dd
                                //Chatting/방이름/채팅내용
                                //OutByForce/ID
                                if (message.contains("ExitRoom")) { //방자체를 나오기
                                    StringTokenizer st = new StringTokenizer(message, "/");
                                    String protocol = st.nextToken();
                                    String Message = st.nextToken();
                                    roomList.remove(Message); //내가 속한 방목록에서 지우기, 방을 나왔으니까
                                    send_message(message);
                                    inRoom = false;
                                } else if (message.contains("Back")) { //대기실로 가기
                                    inRoom = false;
                                } else if (message.contains("OutByForce")) {
                                    if (RoomLeader) {
                                        StringTokenizer st = new StringTokenizer(message, "/");
                                        String protocol = st.nextToken();
                                        String Message = st.nextToken();
                                        send_message("OutByForce/" + My_Room + "/" + Message);
                                        System.out.println("*****" + Message + "님이 강제퇴장되었습니다*****");
                                    } else {
                                        System.out.println("방장일 경우에만 강퇴가능합니다");
                                    }
                                } else {
                                    send_message("Chatting/" + My_Room + "/" + message);
                                }

                            } else {
                                //채팅방 밖에 있을 때,
                                //ShowRoomList/dd
                                //CreateRoom/방이름
                                //JoinRoom/방이름
                                //EnterRoom/방이름 //이미 접속한 방에 다시 들어가기
                                StringTokenizer st = new StringTokenizer(message, "/");
                                String protocol = st.nextToken();
                                String Message = st.nextToken();

                                if (protocol.equals("ShowRoomList")) {
                                    for (int i = 0; i < roomList.size(); i++) {
                                        System.out.println(roomList.get(i));
                                    }
                                } else if (protocol.equals("EnterRoom")) {
                                    System.out.println("이미 참여중인 " + Message + "에 다시 들어옴");
                                    inRoom = true;
                                    My_Room = Message;
                                } else {
                                    send_message(message);
                                }
                            }
                        } catch (NoSuchElementException e) {
                            //e.printStackTrace();
                            System.out.println("형식이 잘못 되었습니다");
                        }
                    }
                }

            }
        });

        sendThread.start();

    }

    public static void main(String[] args) {

        Client client = new Client();
        client.Network();
    }


    private void inmessage(String str) { //서버로 부터 들어오는 모든 메시지
        st = new StringTokenizer(str, "/"); //어떤 문자열을, 어떤 토큰으로 자른것인지

        String protocol = st.nextToken();
        String Message = st.nextToken();

        /*if (protocol.equals("NewUser")) { //새로운 접속자
            userList.add(Message); //사용자의 아이디를 사용자리스트에 추가
            Collections.synchronizedList(userList);
            //System.out.println("##" + Message + "님이 입장하셨습니다");
        } else if (protocol.equals("OldUser")) {
            userList.add(Message);
            Collections.synchronizedList(userList);
        } else*/

        if (protocol.equals("CreateRoom")) { //방을 만들었을때
            roomList.add(Message); //나의 룸리스트에 추가.
            Collections.synchronizedList(roomList);
            My_Room = Message;
            inRoom = true;
            RoomLeader = true;
            System.out.println("방만들기 성공 : " + Message);
            System.out.println("방장입니다");
        } else if (protocol.equals("CreateRoomFail")) {//방만들기 실패
            System.out.println("방만들기 실패, 다시 생성하세요.");
        } /*else if (protocol.equals("New_Room")) {
            roomList.add(Message);
            Collections.synchronizedList(roomList);
            System.out.println("새로운 방 " + Message + "이 만들어졌습니다");
        }*/ else if (protocol.equals("Chatting")) { //채팅
            String msg = st.nextToken(); //채팅내용, Message - 보내는 사람
            System.out.println(Message + " : " + msg);
        } /*else if (protocol.equals("OldRoom")) { //기존의 방목록을 자신의 룸리스트에 더하는것
            roomList.add(Message);
            Collections.synchronizedList(roomList);
        } */ else if (protocol.equals("JoinRoom")) {
            roomList.add(Message); //나의 룸리스트에 추가.
            Collections.synchronizedList(roomList);
            My_Room = Message;
            inRoom = true;
            System.out.println(Message + "채팅방에 입장했습니다");
        } /*else if (protocol.equals("User_out")) {
            userList.remove(Message);
            Collections.synchronizedList(userList);
            System.out.println(Message + "님이 나가셨습니다");
        }*/ else if (protocol.equals("ExitRoom")) { //채팅방에서 나갈때, 누가 퇴장했는지 방사람들에게 알려줌.
            System.out.println("*****" + Message + "님이 나가셨습니다****");
        } else if (protocol.equals("OutByForce")) { //어떤 팀원이 강제퇴장당했음
            String msg = st.nextToken(); //OutByForce/방이름/UserID
            System.out.println("*****" + msg + "님이 강제퇴장되었습니다*****");
        } else if (protocol.equals("Outing")) { //본인이 강제퇴장당했을 때, 룸리스트에서 지우기
            System.out.println("******방에서 강퇴당했습니다********" + Message);
            roomList.remove(Message); //내가 속한 방목록에서 지우기, 방을 나왔으니까
            inRoom = false;
        } else if (protocol.equals("JoinRoomFail")) {
            System.out.println("강제퇴장 당한 방에는 다시 참여할 수 없습니다");
        } else if (protocol.equals("Error")) {
            System.out.println("형식에 맞지 않는 명령어입니다");
        } else {
          //  System.out.println(Message);
        }
    }

    private void send_message(String str) { //서버에게 메세지를 보내는 부분...소켓 아웃풋스트림을 사용해서.
        try {
            dos.writeUTF(str);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
