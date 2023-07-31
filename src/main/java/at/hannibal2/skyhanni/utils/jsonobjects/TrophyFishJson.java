package at.hannibal2.skyhanni.utils.jsonobjects;

import at.hannibal2.skyhanni.features.fishing.TrophyFishInfo;
import com.google.gson.annotations.Expose;

import java.util.Map;

public class TrophyFishJson {
    @Expose
    public Map<String, TrophyFishInfo> trophy_fish;
}