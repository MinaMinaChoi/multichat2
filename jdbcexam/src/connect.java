import javax.print.DocFlavor;
import java.sql.*;
import java.util.Properties;


/**
 * Created by cmina on 2017-06-13.
 */
public class connect {

    // JDBC driver name and database URL
  //  static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    static final String DB_URL = "jdbc:postgres://13.124.77.49:5432/postgres";

    //  Database credentials
    static final String USER = "postgres";
    static final String PASS = "alsdkek123!";

    public static void main(String[] args) {
        Connection conn = null;
        Statement stmt = null;
        try {

            Class.forName("org.postgresql.Driver");

            String hostid = "45";
            String userid = "cmina21";

            conn = DriverManager.getConnection(
                    "jdbc:postgresql://13.124.77.49:5432/postgres", "postgres",
                    "alsdkek123!");

            stmt = conn.createStatement();

            String sql = "INSERT INTO roomuserlist (roomid, userid) VALUES ('"+hostid+"', '"+userid+"')";

            stmt.executeUpdate(sql);

            System.out.print(stmt);


        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
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
        System.out.println("Goodbye!");
    }//end main
}
