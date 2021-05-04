package feed;

public class RedditPost {

    private final String imageurl;
    private final String title;
    private final String url;

    public RedditPost(String imageurl, String title, String url) {
        this.imageurl = imageurl;
        this.title = title;
        this.url = url;
    }

    public String getImageurl() {
        return imageurl;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

}
