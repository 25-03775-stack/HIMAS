package himas;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeatAssessmentService {

    public static final String[] ACTIVITIES = {
        "Walking", "Outdoor Work", "Running",
        "Sports", "Indoor Activity", "Resting"
    };

    public static final String[] PLACES = {
        "Outdoor / open area", "Market / street", "Beach / resort",
        "School / campus", "Mall / air-conditioned"
    };

    public static final String[] CROWDS = {
        "Low (few people)", "Medium", "High (very crowded)"
    };

    private static final int AIRCON_PLACE = 4;
    private static final int CROWD_HIGH = 2;

    private HeatAssessmentService() {
    }

    public static final class Result {
        public String place;
        public String time;
        public double temp;
        public double humidity;
        public double apparent;
        public double heatIndex;
        public String pagasaCategory;
        public int level;
        public String levelName;
        public boolean airCon;
        public boolean crowdRaised;
        public boolean peakHours;
        public String activityName;
        public String placeName;
        public String crowdName;
        public String placeNote;
        public String crowdNote;
        public String headline;
        public String verdict;
        public List<String> safeItems;
        public List<String> avoidItems;
        public String hydration;
    }

    public static Result assess(String query, int actIdx, int placeIdx, int crowdIdx)
            throws Exception {
        Weather w = fetchWeather(query);
        double hi = heatIndexC(w.temp, w.humidity);
        int baseLevel = levelOf(hi);
        boolean airCon = (placeIdx == AIRCON_PLACE);

        int level = baseLevel;
        if (airCon) {
            level = Math.max(0, baseLevel - 1);
        }
        boolean crowdRaised = false;
        if (!airCon && crowdIdx == CROWD_HIGH && baseLevel >= 1 && level < 3) {
            level++;
            crowdRaised = true;
        }

        Result r = new Result();
        r.place = w.place;
        r.time = w.time;
        r.temp = w.temp;
        r.humidity = w.humidity;
        r.apparent = w.apparent;
        r.heatIndex = hi;
        r.pagasaCategory = pagasaCategory(hi);
        r.level = level;
        r.levelName = HeatData.LEVEL_NAMES[level];
        r.airCon = airCon;
        r.crowdRaised = crowdRaised;
        r.peakHours = isPeakHour(w.time);
        r.activityName = ACTIVITIES[actIdx];
        r.placeName = PLACES[placeIdx];
        r.crowdName = CROWDS[crowdIdx];
        r.placeNote = placeNote(placeIdx);
        r.crowdNote = crowdNote(crowdIdx, airCon, crowdRaised);
        r.headline = headline(level, airCon);
        r.verdict = verdict(actIdx, level);
        r.safeItems = safeItems(level);
        r.avoidItems = avoidItems(level);
        r.hydration = hydration(level);
        return r;
    }

    private static Weather fetchWeather(String query) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

        String geo = httpGet(client,
            "https://geocoding-api.open-meteo.com/v1/search?count=1"
            + "&language=en&format=json&name="
            + URLEncoder.encode(query, StandardCharsets.UTF_8));

        if (!geo.contains("\"results\"")) {
            throw new Exception("Location not found: " + query);
        }

        double lat = number(geo, "latitude");
        double lon = number(geo, "longitude");
        String name = text(geo, "name");
        String region = text(geo, "admin1");

        String wx = httpGet(client,
            "https://api.open-meteo.com/v1/forecast?latitude=" + lat
            + "&longitude=" + lon
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature"
            + "&timezone=auto");

        Weather w = new Weather();
        w.place = region.isEmpty() ? name : name + ", " + region;
        w.temp = number(wx, "temperature_2m");
        w.humidity = number(wx, "relative_humidity_2m");
        w.apparent = number(wx, "apparent_temperature");

        Matcher m = Pattern.compile(
            "\"time\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2})\"").matcher(wx);
        w.time = m.find() ? m.group(1) : "";
        return w;
    }

    private static String httpGet(HttpClient client, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .build();
        HttpResponse<String> res = client.send(req,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new Exception("Weather service returned HTTP " + res.statusCode());
        }
        return res.body();
    }

    private static double number(String json, String key) throws Exception {
        Matcher m = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        if (!m.find()) {
            throw new Exception("Weather data missing: " + key);
        }
        return Double.parseDouble(m.group(1));
    }

    private static String text(String json, String key) {
        Matcher m = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static double heatIndexC(double tempC, double rh) {
        double t = tempC * 9.0 / 5.0 + 32.0;

        double hi = 0.5 * (t + 61.0 + ((t - 68.0) * 1.2) + (rh * 0.094));
        if ((hi + t) / 2.0 >= 80.0) {
            hi = -42.379 + 2.04901523 * t + 10.14333127 * rh
                - 0.22475541 * t * rh - 0.00683783 * t * t
                - 0.05481717 * rh * rh + 0.00122874 * t * t * rh
                + 0.00085282 * t * rh * rh - 0.00000199 * t * t * rh * rh;

            if (rh < 13 && t >= 80 && t <= 112) {
                hi -= ((13 - rh) / 4.0)
                    * Math.sqrt((17 - Math.abs(t - 95)) / 17.0);
            } else if (rh > 85 && t >= 80 && t <= 87) {
                hi += ((rh - 85) / 10.0) * ((87 - t) / 5.0);
            }
        }
        return (hi - 32.0) * 5.0 / 9.0;
    }

    private static int levelOf(double hi) {
        if (hi >= 52) return 3;
        if (hi >= 42) return 2;
        if (hi >= 33) return 1;
        return 0;
    }

    private static String pagasaCategory(double hi) {
        if (hi >= 52) return "Extreme Danger";
        if (hi >= 42) return "Danger";
        if (hi >= 33) return "Extreme Caution";
        if (hi >= 27) return "Caution";
        return "Not hazardous";
    }

    private static boolean isPeakHour(String time) {
        if (time.length() < 13) {
            return false;
        }
        int hour = Integer.parseInt(time.substring(11, 13));
        return hour >= 10 && hour < 16;
    }

    private static String headline(int level, boolean airCon) {
        switch (level) {
            case 3:
                return "<b>NOT SAFE TO GO OUT.</b> Stay home or in an "
                    + "air-conditioned place. Go out only for emergencies.";
            case 2:
                return "<b>Dangerous heat.</b> Staying indoors is the safest "
                    + "choice. Postpone non-urgent trips until the evening "
                    + "or a cooler day.";
            case 1:
                return "<b>Be careful.</b> Outdoor activity is possible but "
                    + "should be short, in the shade, and with plenty of water."
                    + (airCon ? " The risk applies mostly to your trip going there." : "");
            default:
                return "<b>Conditions are fine</b> for most activities. "
                    + "Stay hydrated and keep checking the weather.";
        }
    }

    private static String placeNote(int placeIdx) {
        switch (placeIdx) {
            case 0: return "Open areas have little shade and direct sun.";
            case 1: return "Streets and markets trap heat and are often crowded.";
            case 2: return "Sun and reflection from water and sand add heat; "
                    + "humidity near the sea is usually high.";
            case 3: return "Open grounds and queues can be hot; look for covered areas.";
            default: return "Indoor cooling lowers risk, but the trip to and from "
                    + "the place is still exposed to the heat.";
        }
    }

    private static String crowdNote(int crowdIdx, boolean airCon, boolean raised) {
        switch (crowdIdx) {
            case 0: return "Easier to find shade and space.";
            case 1: return "Expect warmer, stuffier spots and queues.";
            default:
                if (raised) {
                    return "Heat and humidity build up in packed spots and shade "
                        + "is limited, so the risk level was raised one step.";
                }
                return "Many people means less airflow; take extra breaks.";
        }
    }

    private static String verdict(int act, int level) {
        int status = HeatData.STATUS[act][level];
        String name = ACTIVITIES[act].toLowerCase();

        if (status == 0) {
            return act >= 4
                ? "✔ <b>SAFE.</b> A good choice for today."
                : "✔ <b>SAFE.</b> You can do " + name + ", but keep drinking water.";
        }
        if (status == 1) {
            return "⚠ <b>LIMIT.</b> Do " + name + " only for a short time, "
                + "before 10 AM or after 4 PM, with shade and water breaks. "
                + "Stop if you feel dizzy, weak, or very tired.";
        }
        if (level == 3) {
            return "⛔ <b>NOT SAFE.</b> Do not do " + name
                + " today. Stay home and rest or do an indoor activity.";
        }
        return "⛔ <b>NOT SAFE.</b> Postpone " + name
            + " to a cooler time or day. Try an indoor activity instead.";
    }

    private static List<String> safeItems(int level) {
        switch (level) {
            case 3:
                return List.of("Rest at home",
                    "Light indoor activity (stretching, reading, chores) in a cool room");
            case 2:
                return List.of("Indoor activity in a cool or air-conditioned place",
                    "Resting",
                    "Short, necessary trips only, in the shade");
            case 1:
                return List.of("Indoor activity and resting",
                    "Short walks early morning or evening",
                    "Light outdoor work with frequent shade breaks");
            default:
                return List.of("Walking, outdoor work, running, and sports, with water and breaks",
                    "Indoor activity and resting");
        }
    }

    private static List<String> avoidItems(int level) {
        switch (level) {
            case 3:
                return List.of("ALL outdoor activity",
                    "Travelling between 10 AM and 4 PM");
            case 2:
                return List.of("Outdoor work, running, and sports",
                    "Long walks under the sun",
                    "Staying outside from 10 AM to 4 PM");
            case 1:
                return List.of("Running and sports under direct sun",
                    "Heavy outdoor work from 10 AM to 4 PM");
            default:
                return List.of("Nothing special. Just avoid long hours without water.");
        }
    }

    private static String hydration(int level) {
        if (level >= 2) {
            return "At this heat level it is hard to stay hydrated outdoors, "
                + "so staying at home or in a cool place is the safest choice. "
                + "Drink water regularly even while resting.";
        }
        return "Drink about a cup (250 ml) of water every 15 to 20 minutes while "
            + "active outdoors. Do not wait until you are thirsty.";
    }

    private static class Weather {
        String place;
        String time;
        double temp;
        double humidity;
        double apparent;
    }
}