package com.seu.magicfilter.utils;


import com.seu.magicfilter.advanced.MagicBeautyFilter;
import com.seu.magicfilter.advanced.MagicCoolFilter;
import com.seu.magicfilter.advanced.MagicRomanceFilter;
import com.seu.magicfilter.advanced.MagicSunriseFilter;
import com.seu.magicfilter.advanced.MagicSunsetFilter;
import com.seu.magicfilter.advanced.MagicWarmFilter;
import com.seu.magicfilter.base.gpuimage.GPUImageFilter;


public class MagicFilterFactory {

    public static GPUImageFilter initFilters(MagicFilterType type) {
        switch (type) {
            case NONE:
                return new GPUImageFilter();
            case BEAUTY:
                return new MagicBeautyFilter();
            case ROMANCE:
                return new MagicRomanceFilter();
            case COOL:
                return new MagicCoolFilter();
            case WARM:
                return new MagicWarmFilter();
            case SUNRISE:
                return new MagicSunriseFilter();
            case SUNSET:
                return new MagicSunsetFilter();
            default:
                return null;
        }
    }
}
