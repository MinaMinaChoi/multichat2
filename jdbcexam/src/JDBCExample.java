import java.sql.*;

/**
 * Created by cmina on 2017-06-13.
 */
public class JDBCExample {

    public static void main(String[] argv) {

        Connection conn = null;
        Statement stmt = null;

        System.out.println("-------- PostgreSQL "
                + "JDBC Connection Testing ------------");

        try {

            Class.forName("org.postgresql.Driver");

        } catch (ClassNotFoundException e) {

            System.out.println("Where is your PostgreSQL JDBC Driver? "
                    + "Include in your library path!");
            e.printStackTrace();
            return;

        }

        System.out.println("PostgreSQL JDBC Driver Registered!");

        Connection connection = null;

        try {

            connection = DriverManager.getConnection(
                    "jdbc:postgresql://13.124.77.49:5432/postgres", "postgres",
                    "alsdkek123!");

            System.out.println("Connected database successfully...");

            //STEP 4: Execute a query
            System.out.println("Creating statement...");
            stmt = connection.createStatement();



            String hostid = "35";
            String userid = "cmina21";


            String sql = "INSERT INTO roomuserlist (roomid, userid) VALUES ('"+hostid+"', '"+userid+"')";

            stmt.executeUpdate(sql);

            System.out.print(stmt);


/*
            String sql = "SELECT * FROM hosttable";
            ResultSet rs = stmt.executeQuery(sql);
            //STEP 5: Extract data from result set
            while (rs.next()) {
                //Retrieve by column name
                int id = rs.getInt("hostid");
                String title = rs.getString("htitle");
                String hdate = rs.getString("hdate");
                String htime = rs.getString("htime");
                String area = rs.getString("harea");
                String brief = rs.getString("hbrief");
                String userid = rs.getString("userid");
                Integer now = rs.getInt("now");

                //Display values
                System.out.print("ID: " + id);
                System.out.print(", title: " + title);
                System.out.print(", hdate: " + hdate);
                System.out.print(", time: " + htime);
                System.out.print(", area : " + area);
                System.out.print(", brief : "+brief);
                System.out.print(", userid : "+userid);
                System.out.println(", now : "+ now);
            }
            rs.close();

*/


        } catch (SQLException e) {

            System.out.println("Connection Failed! Check output console");
            e.printStackTrace();
            return;

        }

        if (connection != null) {
            System.out.println("You made it, take control your database now!");
        } else {
            System.out.println("Failed to make connection!");
        }
    }

}
