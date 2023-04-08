package VASSAL.build.module.index;

import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.counters.GamePiece;
import VASSAL.tools.qtree.QNode;
import VASSAL.tools.qtree.QPoint;
import VASSAL.tools.qtree.QuadTree;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A Quadtree index of pieces on the playable area of the Map.
 * Each node of the Quadtree holds an Arraylist of the Game Pieces at that Map location
 *
 * Pieces that do not reside on the actual map (i.e. in the edge buffer, or off map) will not be
 * indexed and NOT be found by a Ranged GKC
 *
 */
public class VassalMapQuadTree extends QuadTree {

  /** The actual playable bounds of the map **/
  private final Rectangle bounds;

  /** Cross-reference for each piece to it's current location in the Quadtree to allow for quick updates as pieces move
   * This index records the currently recorded location of each piece, key is Piece ID.
   * */
  java.util.Map<String, Point> pieces = new HashMap<>();

  /**
   * Create a QuadTree to cover the entire map
   * @param map
   */
  public VassalMapQuadTree(Map map) {

    super();

    int minX = 9999999;
    int minY = 9999999;
    int maxX = 0;
    int maxY = 0;

    // Find the Playable bounds of the entire Map
    for (final Board b : map.getBoards()) {
      final Rectangle bounds = b.bounds();
      if (bounds.x < minX)
        minX = bounds.x;
      if (bounds.y < minY)
        minY = bounds.y;
      if (bounds.x + bounds.width > maxX)
        maxX = bounds.x + bounds.width;
      if (bounds.y + bounds.height > maxY)
        maxY = bounds.y + bounds.height;
    }

    // Add 100 pixel border for good measure
    minX -= 100;
    maxX += 100;
    minY -= 100;
    maxY += 100;

    setRootNode(new QNode(minX, minY, maxX - minX, maxY - minY, null));

    bounds = new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }

  /**
   * Add a piece to the Qtree, or change it's location
   * @param p Piece to add/move
   */
  public void addOrUpdatePiece(GamePiece p) {
    final Point lastLocation = pieces.get(p.getId());
    final Point currentLocation = p.getPosition();

    // Short-circuit. If the piece hasn't moved and is already in the Qtree, do nothing
    if (lastLocation != null && lastLocation.equals(currentLocation)) {
      return;
    }

    // Remove the piece from it's existing location in Qtree
    if (lastLocation != null) {
      removePiece(p);
    }

    // Record the new position if on the map
    if (currentLocation != null && bounds.contains(currentLocation)) {

      // Grab the list of pieces already at the new location
      final ArrayList<GamePiece> nodePieces = (ArrayList<GamePiece>) get(currentLocation.x, currentLocation.y, new ArrayList<>());

      // Add this piece to the new location and write it back.
      if (!nodePieces.contains(p)) {
        nodePieces.add(p);
        set(currentLocation.x, currentLocation.y, nodePieces);
      }

      // Update the location cross-reference
      pieces.put(p.getId(), currentLocation);
    }
  }

  /**
   * Remove a piece from the Qtree and the cross-reference
   * @param p Piece to remove
   */
  public void removePiece(GamePiece p) {

    // Lookup the location that we added the piece to the Qtree at. Remove it if we find it.
    final Point lastLocation = pieces.remove(p.getId());

    // If lastLocation is null, then the piece does not exist in the Qtree
    if (lastLocation != null) {

      // Grab the list of pieces at that location
      final ArrayList<GamePiece> nodePieces = (ArrayList<GamePiece>) get(lastLocation.x, lastLocation.y, null);

      // Should not be null, but make sure we don't NPE
      if (nodePieces != null) {

        // Remove this piece and write the updated set of pieces back
        nodePieces.remove(p);
        set(lastLocation.x, lastLocation.y, nodePieces);
      }

    }
  }

  /**
   * Return a list of all pieces within range of a given Point
   * NOTE: This is just a pre-selection, we are seacrchign a box, not a circle so extra pieces will be returned that
   * are not in range. This will be handled at the level above which will post-process our selection.
   *
   * @param pos Position
   * @param range range in pixels to search
   * @return List of pieces
   */
  public List<GamePiece> getPiecesInRange(Point pos, int range) {
    final ArrayList<GamePiece> pieces = new ArrayList<>();

    for (final QPoint q : searchWithin(pos.x - range, pos.y - range, pos.x + range, pos.y + range)) {
      pieces.addAll((ArrayList<GamePiece>) q.getValue());
    }

    return pieces;
  }

}
