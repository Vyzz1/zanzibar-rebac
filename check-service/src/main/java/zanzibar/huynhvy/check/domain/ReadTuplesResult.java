package zanzibar.huynhvy.check.domain;

import java.util.List;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * One page of a Read: the matched tuples plus an opaque cursor to fetch the next page ({@code ""}
 * when the page is the last).
 */
public record ReadTuplesResult(List<RelationTuple> tuples, String nextPageToken) {}
