package feed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;
import javax.faces.context.FacesContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@ManagedBean(name = "getfeed")
@RequestScoped
public class FeedRequestBean {

    private PreparedStatement ps = null;
    private static final String dbURL = "jdbc:derby://localhost:1527/web-agg";
    private static final String dbUser = "root";
    private static final String dbPass = "password";
    private static Connection conn = null;

    private List<RedditPost> postList;
    private String token = "";

    public FeedRequestBean() {
        initJDBC();
    }

    //Returns posts as a List<>
    public List<RedditPost> getPostList() {
        String statecode = "";
        String next = "";

        //getting request and response objects
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        HttpServletResponse response = (HttpServletResponse) FacesContext.getCurrentInstance().getExternalContext().getResponse();

        //parsing through cookies for statecode at next code
        Cookie ck[] = request.getCookies();
        for (Cookie ck1 : ck) {
            if (ck1.getName().equals("web-agg")) {
                statecode = ck1.getValue();
                break;
            }
            if (ck1.getName().equals("next")) {
                next = ck1.getValue();
                break;
            }
        }

        
        getToken(statecode);                                        //retrieving token from database
        this.postList = getListing(this.token, next, response);     //retrieving posts from reddit

        return this.postList;
    }

    //Initializes PreparedStatement for getToken()
    private void initJDBC() {
        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
            conn = DriverManager.getConnection(dbURL, dbUser, dbPass);
            conn.setAutoCommit(false);

            ps = conn.prepareStatement("SELECT * FROM users WHERE statecode = ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SQLException ex) {
            ex.printStackTrace();
        }
    }

    //Returns the token matching the statecode
    private String getToken(String statecode) {
        String returnString = "not_found";

        if (!token.isEmpty()) {
            return token;
        }

        try {
            ps.setString(1, statecode);
            ResultSet query = ps.executeQuery();

            if (query.next()) {
                token = query.getString("token");
                return token;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return returnString;
    }

    //returns the listing or next listing when given the token, after and response
    private List<RedditPost> getListing(String token, String next, HttpServletResponse response) {
        List<RedditPost> posts = new ArrayList<>();
        try {
            //Preparing URL and parameters for request
            String url = "https://oauth.reddit.com/best.json";
            if (!next.isEmpty()) {
                url += "?after=" + next;
            }

            //Creating URL object
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

            //Setting request properties
            connection.setRequestMethod("GET");
            connection.addRequestProperty("User-Agent", "web-agg by /u/Mr_Patcher");
            connection.addRequestProperty("Authorization", "bearer " + token);

            //Running Connection
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                //Building response json
                StringBuilder listingResponse = new StringBuilder();
                try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = inputReader.readLine()) != null) {
                        listingResponse.append(inputLine);
                    }
                }

                //Parsing json
                JSONParser jsonParser = new JSONParser();
                JSONObject json = (JSONObject) jsonParser.parse(listingResponse.toString());

                String kind = (String) json.get("kind");
                if (kind.equals("Listing")) {
                    JSONObject listingData = (JSONObject) json.get("data");

                    //saving after value as a cookie with name "next"
                    next = (String) listingData.get("after");
                    Cookie cnext = new Cookie("next", next);
                    cnext.setMaxAge(3600);
                    response.addCookie(cnext);

                    //retrieving posts and adding them to the ArrayList<> 
                    JSONArray redditposts = (JSONArray) listingData.get("children");

                    Iterator postsIterator = redditposts.iterator();
                    while (postsIterator.hasNext()) {
                        JSONObject post = (JSONObject) postsIterator.next();
                        JSONObject postData = (JSONObject) post.get("data");

                        String postTitle = (String) postData.get("title");
                        String postUrl = "https://www.reddit.com";
                        postUrl += (String) postData.get("permalink");
                        String postThumbnail = (String) postData.get("thumbnail");

                        posts.add(new RedditPost(postThumbnail, postTitle, postUrl));
                    }
                }

                return posts;
            } else {
                System.out.println("-----Response Code : " + responseCode);
            }
        } catch (IOException | ParseException ex) {
            ex.printStackTrace();
        }

        return posts;
    }

}
