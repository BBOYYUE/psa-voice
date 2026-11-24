package com.sollado.psavoice;
public final class TrackMatcherCheck {
  public static void main(String[] args) {
    // 中文歌手+歌名：全匹配且严格。
    expect("周杰伦 晴天", "晴天", "周杰伦", true, true);
    // 无空格连写：单 token 同时包含歌名与歌手。
    expect("周杰伦晴天", "晴天", "周杰伦", true, true);
    // 英文多词歌名。
    expect("Taylor Swift Love Story", "Love Story", "Taylor Swift", true, true);
    // 全角空格与全角字符（NFKC 归一化）。
    expect("周杰伦　晴天", "晴天", "周杰伦", true, true);
    expect("ｌｏｖｅ　ｓｔｏｒｙ", "Love Story", "Taylor Swift", true, true);
    // 句末标点不影响匹配。
    expect("晴天。", "晴天", "周杰伦", true, true);
    // 含字母 s/p 的词不能被错误剔除字母。
    expect("Supper Moment 无尽", "无尽", "Supper Moment", true, true);
    expect("Oasis Wonderwall", "Wonderwall", "Oasis", true, true);
    // 旧歌曲不得误报：歌名只是更长标题的子串。
    expect("周杰伦 晴天", "晴天娃娃", "周杰伦", true, false);
    // 旧歌曲不得误报：歌手命中但歌名无关。
    expect("周杰伦 晴天", "Mojito", "周杰伦", false, false);
    // 纯歌手请求：匹配但不严格，必须等曲目变化才能确认。
    expect("周杰伦", "晴天", "周杰伦", true, false);
    expect("周杰伦", "晴天", "蔡依林", false, false);
    // 词被拆到标题与歌手两侧都算命中。
    expect("平凡之路 朴树", "平凡之路", "朴树", true, true);
    // 标题带版本后缀时降级为非严格匹配。
    expect("晴天", "晴天 (Live)", "周杰伦", true, false);
    // 空信息与空查询永不匹配。
    expect("", "晴天", "周杰伦", false, false);
    expect("晴天", null, null, false, false);
    expect("  ", "晴天", "周杰伦", false, false);
    if (!TrackMatcher.trackKey("晴天", "周杰伦").equals(TrackMatcher.trackKey(" 晴天 ", " 周杰伦 ")))
      throw new AssertionError("trackKey must ignore whitespace");
    System.out.println("Track matcher checks passed: Chinese, English, full-width, punctuation, s/p words, strict vs loose.");
  }
  private static void expect(String query, String title, String artist, boolean matched, boolean strict) {
    TrackMatcher.Result result = TrackMatcher.match(query, title, artist);
    if (result.matched != matched || result.strict != strict)
      throw new AssertionError("query=" + query + " title=" + title + " artist=" + artist
          + " expected matched=" + matched + " strict=" + strict
          + " but got matched=" + result.matched + " strict=" + result.strict);
  }
}
