/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2017.
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
package ffx.ui.behaviors;

import javax.media.j3d.TransformGroup;

/**
 * The PickingCallback interface is implemented by classes wishing to recieve
 * notification that a picked object has moved.
 *
 * @author Michael J. Schnieders
 *
 */
public interface PickingCallback {

    /**
     * Constant <code>ROTATE=0</code>
     */
    public final static int ROTATE = 0;
    /**
     * Constant <code>TRANSLATE=1</code>
     */
    public final static int TRANSLATE = 1;
    /**
     * Constant <code>ZOOM=2</code>
     */
    public final static int ZOOM = 2;
    /**
     * Constant <code>SELECTION=4</code>
     */
    public final static int SELECTION = 4;
    /**
     * Constant <code>PROPERTIES=5</code>
     */
    public final static int PROPERTIES = 5;
    /**
     * Constant <code>ORBIT=6</code>
     */
    public final static int ORBIT = 6;
    /*
     * The user made a selection but nothing was actually picked
     */
    /**
     * Constant <code>NO_PICK=3</code>
     */
    public final static int NO_PICK = 3;

    /*
     * Called by the Pick Behavior with which this callback is registered each
     * time the Picked object is moved
     */
    /**
     * <p>
     * transformChanged</p>
     *
     * @param type a int.
     * @param tg a {@link javax.media.j3d.TransformGroup} object.
     */
    public void transformChanged(int type, TransformGroup tg);

    /**
     * <p>
     * transformClicked</p>
     *
     * @param type a int.
     * @param tg a {@link javax.media.j3d.TransformGroup} object.
     */
    public void transformClicked(int type, TransformGroup tg);

    /**
     * <p>
     * transformDoubleClicked</p>
     *
     * @param type a int.
     * @param tg a {@link javax.media.j3d.TransformGroup} object.
     */
    public void transformDoubleClicked(int type, TransformGroup tg);
}
/*
 * Copyright (c) 1996-1998 Sun MicroFSystems, Inc. All Rights Reserved. Sun
 * grants you ("Licensee") a non-exclusive, royalty free, license to use, modify
 * and redistribute this software in source and binary code form, provided that
 * i) this copyright notice and license appear on all copies of the software;
 * and ii) Licensee does not utilize the software in a manner which is
 * disparaging to Sun. This software is provided "AS IS," without a warranty of
 * any kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFXRED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL
 * SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES,
 * HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE
 * USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES. This software is not designed or intended for
 * use in on-line control of aircraft, air traffic, aircraft navigation or
 * aircraft communications; or in the design, construction, operation or
 * maintenance of any nuclear facility. Licensee represents and warrants that it
 * will not use or redistribute the Software for such purposes.
 */
