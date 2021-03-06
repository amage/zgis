package org.mati.geotech.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;

import org.mati.geotech.gui.TexturesStorage;
import org.mati.geotech.model.cellcover.CellCoverListener;
import org.mati.geotech.model.cellcover.MapGridCellView;
import org.mati.geotech.model.qmap.GoogleMapPathMaker;
import org.mati.geotech.model.qmap.PathMaker;
import org.mati.geotech.model.qmap.VirtualMapPathMaker;

import com.sun.opengl.util.texture.Texture;
import com.sun.opengl.util.texture.TextureData;
import com.sun.opengl.util.texture.TextureIO;

public class ResManager implements TextureProcListener, CellCoverListener {

    public enum MapSource {
        GOOGLE, VIRTUAL_EARTH_LINES, VIRTUAL_EARTH_PHOTO, VIRTUAL_EARTH_ALL
    };

    private MapSource _curMapSrc = MapSource.VIRTUAL_EARTH_ALL;

    private TextureProc _texProc;
    private int _cacheLvls = 3;
    private HashMap<String, Texture> _texsActive[];

    private Texture _texMatrix[][][];
    private double _gsw = 0;
    private double _gsh = 0;

    private Rect _map = new Rect(-180, -90, 360, 180);

    private int _lvl = 1;
    private Rect _cacheRect = new Rect(0, 0, 0, 0);

    // Interaction stuff
    private Vector<ResManagerListener> _rmi = new Vector<ResManagerListener>();

    private TexturesStorage texStore;

    public void removeListner(ResManagerListener rmi) {
        _rmi.remove(rmi);
    }

    public void addListner(ResManagerListener rmi) {
        _rmi.add(rmi);
    }

    @SuppressWarnings("unchecked")
    public ResManager() {

        _texProc = new TextureProc();
        _texsActive = new HashMap[_cacheLvls];
        _texsActive[0] = new HashMap<String, Texture>();
        _texsActive[1] = new HashMap<String, Texture>();
        _texsActive[2] = new HashMap<String, Texture>();
        _map.setSameGeometry(new Rect(-180, -90, 360, 180));
        _texMatrix = new Texture[3][][];
    }

    public MapSource getMapSourceType() {
        return _curMapSrc;
    }

    public void setMapSourceType(MapSource ms) {
        _curMapSrc = ms;
        _texProc.setMapSourceType(ms);
        for (int i = 0; i < 3; i++)
            _texsActive[i].clear();
        for (ResManagerListener rm : _rmi)
            rm.stateChanged();
    }

    synchronized private void preload(Rect r, int lvl) {
        int layer = lvl - _lvl + 1;
        Vector<String> paths = getPathsFor(r, lvl);
        // System.out.println("preload"+"("+layer+")"+": " + paths.size());
        for (String str : paths) {
            try {
                _texsActive[layer].put(str, null);
                getMapTexture(str);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static PathMaker selectPathMaker(MapSource mapSource) {
        switch (mapSource) {
        case GOOGLE:
            return new GoogleMapPathMaker();
        case VIRTUAL_EARTH_LINES:
            return new VirtualMapPathMaker();
        case VIRTUAL_EARTH_PHOTO:
            return new VirtualMapPathMaker();
        case VIRTUAL_EARTH_ALL:
            return new VirtualMapPathMaker();
        }
        throw new IllegalArgumentException();
    }

    /*
     * synchronized private void freeTextures(Rect r, int lvl) { int layer =
     * lvl-_lastLvl+1;
     * 
     * Vector<String> paths = getPathsFor(r, lvl);
     * System.out.println("free("+layer+"): " + paths.size()); for(String
     * str:paths) { try { if(_texsActive[layer].get(str)!=null)
     * _texsActive[layer].get(str).dispose(); _texProc.freeTexture(str); } catch
     * (Exception e) { e.printStackTrace(); } } }
     */

    private boolean isLoaded(Texture t) {
        return t != null && t != texStore.getDownloadingTexture()
                && t != texStore.getLoadingTexture()
                && t != texStore.getNotAvailableTexture();
    }

    private void setViewWindow(Rect vp, int lvl) {
        if (_lvl != lvl) {
            int shift = lvl - _lvl;
            _lvl = lvl;

            // System.out.println("shift: " + shift);

            int cleanLayer = shift == 1 ? 0 : 2;

            for (String str : _texsActive[cleanLayer].keySet()) {
                _texProc.freeTexture(str);
                Texture t = _texsActive[cleanLayer].get(str);
                if (isLoaded(t)) {
                    t.bind();
                    t.dispose();
                }
            }
            _texsActive[cleanLayer].clear();
            System.gc();

            if (shift == 1) {
                _texsActive[0] = _texsActive[1];
                _texsActive[1] = _texsActive[2];
                _texsActive[2] = new HashMap<String, Texture>();
                preload(_cacheRect, lvl + 1);
            } else if (shift == -1) {
                _texsActive[2] = _texsActive[1];
                _texsActive[1] = _texsActive[0];
                _texsActive[0] = new HashMap<String, Texture>();
                preload(_cacheRect, _lvl - 1);
            } else {
                System.out.println("RELOAD ALL CACHE!");
                for (int i = 0; i < 3; i++) {
                    for (String str : _texsActive[i].keySet()) {
                        _texProc.freeTexture(str);
                        Texture t = _texsActive[i].get(str);
                        if (isLoaded(t)) {
                            t.bind();
                            t.dispose();
                        }
                    }
                    _texsActive[i].clear();
                    System.gc();
                    preload(_cacheRect, _lvl - 1 + i);
                }
            }
        }
    }

    /*
     * synchronized private void updateCache(Rect rect, Rect window) { // Rect
     * rLoad[] = { new Rect(0,0,0,0), new Rect(0,0,0,0) }; // Rect rFree[] =
     * {new Rect(0,0,0,0), new Rect(0,0,0,0) };
     * 
     * // preload(rLoad[0], _lastLvl-1); preload(rLoad[0], _lastLvl);
     * preload(rLoad[0], _lastLvl+1); }
     */

    synchronized private void loadTextures() throws Exception {
        _texProc.addListner(this);
        _texProc.start();

        for (int i = 0; i < 3; i++) {
            Vector<String> paths = getPathsFor(_map, i + 1);
            for (String str : paths) {
                _texsActive[i].put(str, getMapTexture(str));
            }
        }
        texStore = new TexturesStorage();
    }

    public Texture getMapTexture(String gpath) throws Exception {
        if(texStore==null)return null;
        Texture tex = null;
        HashMap<String, Texture> lay = null;

        for (HashMap<String, Texture> hm : _texsActive) {
            if (tex == null) {
                tex = hm.get(gpath);
                lay = hm;
            }
        }

        if (isLoaded(tex))
            return tex;
        try {
            TextureData texData = _texProc.getTextureData(gpath);
            if (texData != null) {
                tex = TextureIO.newTexture(texData);
                lay.put(gpath, tex);
                return tex;
            } else {
                lay.put(gpath, texStore.getLoadingTexture());
                return texStore.getLoadingTexture();
            }
        } catch (TextureNotAvailableException e) {
            if (e.isDownloadig()) {
                lay.put(gpath, texStore.getDownloadingTexture());
                return texStore.getDownloadingTexture();
            } else {
                lay.put(gpath, texStore.getNotAvailableTexture());
                return texStore.getNotAvailableTexture();
            }
        }
    }

    public void init() throws Exception {
        loadTextures();
    }

    public Vector<String> getPathsFor(Rect cell, int lvl) {
        double _cellSize = _map.getWidth() / (Math.pow(2, lvl));
        int n = (int) Math.ceil(cell.getHeight() / _cellSize) * 2 + 1;
        int m = (int) Math.ceil(cell.getWidth() / _cellSize) + 1;

        double cellW = _cellSize;
        double cellH = _cellSize / 2;

        double gx = Math.floor(cell.getX() / cellW) * cellW;
        double gy = Math.floor(cell.getY() / cellH) * cellH;

        Rect r = new Rect(0, 0, 0, 0);

        LinkedList<String> result = new LinkedList<String>();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                r.setX(j * cellW + gx);
                r.setY(i * cellH + gy);
                r.setWidth(cellW);
                r.setHeight(cellH);
                if (r.haveOverlap(_map))
                    result.add(makePathFor(r));
            }
        }
        Vector<String> res = new Vector<String>(result);
        return res;
    }

    public String makePathFor(Rect cell) {
        PathMaker pm = selectPathMaker(_curMapSrc);
        return pm.makePathFor(cell, _map);
    }

    @Override
    public void texturesReady(String gpaths[]) {
        for (String gpath : gpaths) {
            if (gpath.length() - _lvl > 2 || gpath.length() - _lvl < 0) {
                _texProc.freeTexture(gpath);
            } else {
                // System.out.println(gpath);
                _texsActive[gpath.length() - _lvl].put(gpath, null);
            }
        }
        for (ResManagerListener rm : _rmi)
            rm.stateChanged();
    }

    @Override
    public void downloadComplite(String name) {
        for (ResManagerListener rm : _rmi)
            rm.stateChanged();
    }

    // CellCover listener
    @Override
    public void gridSizeChanged(int n, int m) {
        // System.out.println("new grid: "+n+"x"+m);
        /*
         * // TODO: use old tex for(int l = 0; l < 3; l++) { _texMatrix[l] = new
         * Texture [n][m]; for(int i = 0; i < _texMatrix[l].length; i++) {
         * for(int j = 0; j < _texMatrix[l][i].length; j++)
         * _texMatrix[l][i][j]=null; } }
         */
    }

    @Override
    public void gridPositionChanged(double x, double y, double cw, double ch,
            int n, int m) {
        _cacheRect.setGeometry(x, y, cw * m, ch * m);
        _gsw = cw;
        _gsh = ch;
        setViewWindow(_cacheRect, _lvl);
        // System.out.println("new grid pos: "+y+"x"+x);
    }

    @Override
    public void levelChanged(int newLvl, int prevLvl) {
        _lvl = newLvl;
    }

    public Texture getMapTexture(MapGridCellView cell) {
        if (cell.haveOverlap(_cacheRect)) {
            int i = (int) ((cell.getY() - _cacheRect.getY()) / (double) _gsh);
            int j = (int) ((cell.getX() - _cacheRect.getX()) / (double) _gsw);
            if (_texMatrix[1][i][j] == null) {
                try {
                    _texMatrix[1][i][j] = getMapTexture(makePathFor(cell));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return _texMatrix[1][i][j];
        } else
            return null;
    }

    public int getMapLvl() {
        return _lvl;
    }

}
