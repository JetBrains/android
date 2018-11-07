const PRIMARY_COLOR = '33, 145, 251, ';
const SECONDARY_COLOR = '186, 39, 74, ';
const PRIMARY_BORDER_COLOR = '140, 222, 220, ';
const SECONDARY_BORDER_COLOR = '132, 28, 38, ';
const FADE_TIME = 250;
const OPACITY = 0.05;
const FRAME_SCALE = 0.25;

class Visualizer {
    constructor(canvasElement, filterElement, componentsElement) {
        this.canvas = canvasElement;
        // An element which, if set, means only elements in that subtree should render.
        this.rootFilterElement = filterElement;
        this.componentsRendered = componentsElement;
        this.ctx = this.canvas.getContext("2d");
        this.windows = {};
        this.buffers = {};
        this.rectGroups = [];
        this.lastStartLoop = window.performance.now();
        this.mostRecentFrameBounds = [0, 0, 1600, 1200];
        this.setup();
        this.draw = this.draw.bind(this);
        this.processInput = this.processInput.bind(this);
    }
    setup() {
        this.ctx.fillStyle = 'rgb(0,0,0)';
        this.fillRect(
            this.mostRecentFrameBounds[0],
            this.mostRecentFrameBounds[1],
            this.mostRecentFrameBounds[2],
            this.mostRecentFrameBounds[3]);
    }

    fillRect(x, y, w, h) {
        this.ctx.fillRect(x * FRAME_SCALE, y * FRAME_SCALE, w * FRAME_SCALE, h * FRAME_SCALE);
    }

    strokeRect(x, y, w, h) {
        this.ctx.strokeRect(x * FRAME_SCALE, y * FRAME_SCALE, w * FRAME_SCALE, h * FRAME_SCALE);
    }

    isRootFilterEnabled(rootFilter) {
        return !!rootFilter;
    }

    isRootFilterDisabled(rootFilter) {
        return !rootFilter;
    }

    // Process each complete JSON tree, and split up ThreadStats individually for processing.
    processInput(rootNode) {
        var result = {
            "alpha": OPACITY,
            "primary": [],
            "secondary": [],
            "ideRootPaneBounds": this.mostRecentFrameBounds.slice()
        };
        var rootFilter = this.rootFilterElement.value;
        for (var i = 0; i < rootNode.length; i++) {
            this.ingest(result, rootNode[i], [0.0, 0.0], null, true, [0.0, 0.0], this.isRootFilterEnabled(rootFilter) ? rootFilter : null);
        }
        this.componentsRendered.innerHTML = "Components Rendered: " + (result.primary.length + result.secondary.length);
        this.rectGroups.push(result);
    }

    // Main render loop to process all rectangles and frame resizes.
    draw() {
        var startLoop = window.performance.now();
        var timeDiff = startLoop - this.lastStartLoop;
        this.lastStartLoop = startLoop;

        var numGroups = this.rectGroups.length;
        if (numGroups === 0) {
            return;
        }

        // Calculate what needs to be cleared if the main window has resized.
        var clearSize = this.mostRecentFrameBounds.slice();
        this.mostRecentFrameBounds = this.rectGroups[numGroups - 1].ideRootPaneBounds.slice();
        for (var i = 0; i < numGroups; i++) {
            var ideBounds = this.rectGroups[i].ideRootPaneBounds;
            clearSize = [
                Math.min(clearSize[0], ideBounds[0]),
                Math.min(clearSize[1], ideBounds[1]),
                Math.max(clearSize[2] + clearSize[0], ideBounds[0] + ideBounds[2]), // Record the right-most x-coordinate of the clear rectangle.
                Math.max(clearSize[3] + clearSize[1], ideBounds[1] + ideBounds[3])  // Record the bottom-most y-coordinate side of the clear rectangle.
            ];
        }
        clearSize[2] -= clearSize[0]; // Recover the width.
        clearSize[3] -= clearSize[1]; // Recover the height.

        if (this.mostRecentFrameBounds[0] !== clearSize[0] ||
            this.mostRecentFrameBounds[1] !== clearSize[1] ||
            this.mostRecentFrameBounds[2] !== clearSize[2] ||
            this.mostRecentFrameBounds[3] !== clearSize[3]) {
            this.ctx.fillStyle = 'rgb(255,255,255)';
            // The floor and ceil deal with partial clears due to subpixel coordinates.
            this.fillRect(
                Math.floor(clearSize[0]),
                Math.floor(clearSize[1]),
                Math.ceil((clearSize[0] + clearSize[2]) - Math.floor(clearSize[0])),
                Math.ceil((clearSize[1] + clearSize[3]) - Math.floor(clearSize[1])));
        }

        this.ctx.fillStyle = 'rgb(0,0,0,255)';
        this.fillRect(this.mostRecentFrameBounds[0], this.mostRecentFrameBounds[1], this.mostRecentFrameBounds[2], this.mostRecentFrameBounds[3]);
        var alphaReduction = timeDiff / FADE_TIME * OPACITY;
        for (var i = 0; i < numGroups; i++) {
            var rectGroup = this.rectGroups.shift();
            if (rectGroup.alpha <= alphaReduction) {
                continue;
            }
            if (rectGroup.ideRootPaneBounds[0] != this.mostRecentFrameBounds[0] ||
                    rectGroup.ideRootPaneBounds[1] != this.mostRecentFrameBounds[1] ||
                    rectGroup.ideRootPaneBounds[2] != this.mostRecentFrameBounds[2] ||
                    rectGroup.ideRootPaneBounds[3] != this.mostRecentFrameBounds[3]) {
                continue;
            }

            rectGroup.alpha -= alphaReduction;
            this.rectGroups.push(rectGroup);

            this.ctx.strokeStyle = 'rgba(' + PRIMARY_BORDER_COLOR + rectGroup.alpha + ')';
            this.ctx.fillStyle = 'rgba(' + PRIMARY_COLOR + rectGroup.alpha + ')';
            for (var j in rectGroup.primary) {
                var rect = rectGroup.primary[j];
                // The OS window bounds are set via the OS while the content bounds are set via Swing.
                // The difference causes rectangles to get drawn outside of the window bounds.
                // One way to fix this is to clamp the fillRect to the window bounds here.
                this.fillRect(rect[0], rect[1], rect[2], rect[3]);
                this.strokeRect(rect[0], rect[1], rect[2], rect[3]);
            }

            if (rectGroup.secondary) {
                this.ctx.strokeStyle = 'rgba(' + SECONDARY_BORDER_COLOR + rectGroup.alpha + ')';
                this.ctx.fillStyle = 'rgba(' + SECONDARY_COLOR + rectGroup.alpha + ')'
                for (var j in rectGroup.secondary) {
                    var rect = rectGroup.secondary[j];
                    this.fillRect(rect[0], rect[1], rect[2], rect[3]);
                    this.strokeRect(rect[0], rect[1], rect[2], rect[3]);
                }
            }
        }
        var startLoop = window.performance.now();
    }
    /**
    * Recursively ingests the JSON tree rooted at the ThreadStat level.
    *
    * @param result Stateful output of the method.
    * @param node The swingp node being processed at this step of the recursion.
    * @param anchor Updated translate position for use by the current node.
    * @param windowNode The heavy weight window in which the current node may fall under.
    * @param isPrimary Whether or not the current node resides under the main heavy weight Window, or some secondary Window (e.g. popup).
    * @param tiledOffset If the painting is done via tiled repaint manager strategy, we need to account for an extra offset.
    * @param rootFilter A string representing a filter for nodes to be rendered (by Java class), or null for no filter.
    */
    ingest(result, node, anchor, windowNode, isPrimary, tiledOffset, rootFilter) {
        var xform = node.xform;

        if (node.classType == "ThreadStat") {
            for (event in node.events) {
                this.ingest(result, node.events[event], anchor, windowNode, isPrimary, tiledOffset, rootFilter);
            }
            return;
        }

        var newAnchor = anchor.slice(); // Anchor should always be in device space (e.g. in HiDPI space if HiDPI is available).
        if (node.classType === "WindowPaintMethodStat") {
            // We hit this node when there are more than one heavy weight component being rendered (e.g. main window + tooltip).
            var ownerWindowId = node.ownerWindowId;
            var parentPosition = this.windows[ownerWindowId] ? this.windows[ownerWindowId].anchor : [0, 0];
            newAnchor = [
                node.location[0] * xform[0] + xform[4] + parentPosition[0],
                node.location[1] * xform[3] + xform[5] + parentPosition[1],
            ];
            windowNode = {"anchor": newAnchor.slice()};
            this.windows[node.windowId] = windowNode;
        }
        else if (node.classType === "BufferStrategyPaintMethodStat") {
            if (!node.isBufferStrategy) {
                newAnchor[0] += tiledOffset[0];
                newAnchor[1] += tiledOffset[1];
            }
        }
        else if (node.classType === "PaintImmediatelyMethodStat") {
            if (node.bufferType === "IdeRootPane") {
                result.ideRootPaneBounds = [
                    node.bufferBounds[0] * xform[0],
                    node.bufferBounds[1] * xform[3],
                    node.bufferBounds[2] * xform[0],
                    node.bufferBounds[3] * xform[3]
                ];
            }
            else {
                var windowAnchor = [0, 0];
                if (windowNode !== null) {
                    this.buffers[node.bufferId] = {
                        "window": windowNode,
                        "bufferType": node.bufferType,
                    }
                    windowAnchor = windowNode.anchor;
                }
                else if (this.buffers[node.bufferId]) {
                    windowAnchor = this.buffers[node.bufferId].window.anchor;
                }
                newAnchor[0] = windowAnchor[0];
                newAnchor[1] = windowAnchor[1];
                isPrimary = false;
            }
            newAnchor[0] += node.constrain[0];
            newAnchor[1] += node.constrain[1];
            // For tiled buffering, add the (x, y) of the bounds, since the paintImmediatelyImpl method deliberately offsets to render to the top-left corner of the buffer.
            tiledOffset = [node.bounds[0] * xform[0], node.bounds[1] * xform[3]];
        }
        else if (node.classType === "PaintComponentMethodStat" && !node.isImage && this.isRootFilterDisabled(rootFilter)) {
            var bounds = node.clip;

            var rect = [
                bounds[0] * xform[0] + xform[4] + newAnchor[0],
                bounds[1] * xform[3] + xform[5] + newAnchor[1],
                bounds[2] * xform[0],
                bounds[3] * xform[3]
            ];
            isPrimary ? result.primary.push(rect) : result.secondary.push(rect);
        }
        else if (node.classType === "PaintChildrenMethodStat" && this.isRootFilterEnabled(rootFilter)) {
            rootFilter = node.pathToRoot.includes(rootFilter) ? null : rootFilter;
        }

        node.callee.forEach(callee => {
            this.ingest(result, callee, newAnchor, windowNode, isPrimary, tiledOffset, rootFilter);
        });
    }
}