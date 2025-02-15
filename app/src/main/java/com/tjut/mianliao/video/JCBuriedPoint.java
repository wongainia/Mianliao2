package com.tjut.mianliao.video;

/**
 * Created by Nathen
 * On 2016/04/04 22:13
 */
public interface JCBuriedPoint {

    void onClickStartIcon(String url, Object... objects);

    void onClickStartError(String url, Object... objects);

    void onClickStop(String url, Object... objects);

    void onClickStopFullscreen(String url, Object... objects);

    void onClickResume(String url, Object... objects);

    void onClickResumeFullscreen(String url, Object... objects);

    void onClickSeekbar(String url, Object... objects);

    void onClickSeekbarFullscreen(String url, Object... objects);

    void onAutoComplete(String url, Object... objects);

    void onAutoCompleteFullscreen(String url, Object... objects);

    void onEnterFullscreen(String url, Object... objects);

    void onQuitFullscreen(String url, Object... objects);

}
