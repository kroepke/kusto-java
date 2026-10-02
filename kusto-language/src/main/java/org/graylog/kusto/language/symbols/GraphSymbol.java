// Ported from: src/Kusto.Language/Symbols/GraphSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.List;

/// <summary>
/// A symbol representing a graph
/// </summary>
public class GraphSymbol extends TypeSymbol
{
    /// <summary>
    /// The shape an edge in the graph.
    /// </summary>
    private final TableSymbol edgeShape;
    public TableSymbol edgeShape() { return this.edgeShape; }

    /// <summary>
    /// The shape of an node in the graph.
    /// This may be null.
    /// </summary>
    private final TableSymbol nodeShape;
    public TableSymbol nodeShape() { return this.nodeShape; }

    public GraphSymbol(String name, TableSymbol edgeShape, TableSymbol nodeShape)
    {
        super(name);
        this.edgeShape = edgeShape != null ? edgeShape : TableSymbol.Empty; // PORT: §3.14 ??
        this.nodeShape = nodeShape;
    }

    public GraphSymbol(String name, TableSymbol edgeShape) // PORT: §3.12 optional parameter nodeShape = null
    {
        this(name, edgeShape, (TableSymbol) null);
    }

    public GraphSymbol(String name, TableSymbol edgeShape, List<TableSymbol> nodeShapes)
    {
        super(name);
        this.edgeShape = edgeShape != null ? edgeShape : TableSymbol.Empty; // PORT: §3.14 ??
        this.nodeShape = nodeShapes != null && nodeShapes.size() > 0 ? TableSymbol.combine(CombineKind.UnifySameName, nodeShapes) : null;
    }

    public GraphSymbol(String name, List<TableSymbol> edgeShapes, List<TableSymbol> nodeShapes)
    {
        super(name);
        this.edgeShape = edgeShapes != null && edgeShapes.size() > 0 ? TableSymbol.combine(CombineKind.UnifySameName, edgeShapes) : null;
        this.nodeShape = nodeShapes != null && nodeShapes.size() > 0 ? TableSymbol.combine(CombineKind.UnifySameName, nodeShapes) : null;
    }

    public GraphSymbol(TableSymbol edgeShape, TableSymbol nodeShape)
    {
        this("", edgeShape, nodeShape);
    }

    public GraphSymbol(TableSymbol edgeShape) // PORT: §3.12 optional parameter nodeShape = null
    {
        this("", edgeShape, (TableSymbol) null);
    }

    public GraphSymbol(TableSymbol edgeShape, List<TableSymbol> nodeShapes)
    {
        this("", edgeShape, nodeShapes);
    }

    public GraphSymbol(List<TableSymbol> edgeShapes, List<TableSymbol> nodeShapes)
    {
        this("", edgeShapes, nodeShapes);
    }

    public GraphSymbol(String name, String edgeSchema, String nodeSchema)
    {
        this(name,
              edgeSchema != null ? TableSymbol.from(edgeSchema) : null,
              nodeSchema != null ? TableSymbol.from(nodeSchema) : null);
    }

    public GraphSymbol(String name, String edgeSchema) // PORT: §3.12 optional parameter nodeSchema = null
    {
        this(name, edgeSchema, (String) null);
    }

    @Override
    public SymbolKind kind() { return SymbolKind.Graph; }

    @Override
    public Tabularity tabularity() { return Tabularity.Other; }

    public GraphSymbol withName(String name)
    {
        if (name != null)
        {
            return new GraphSymbol(name, this.edgeShape(), this.nodeShape());
        }
        else
        {
            return this;
        }
    }

    public GraphSymbol withEdgeShape(TableSymbol edgeShape)
    {
        if (this.edgeShape() != edgeShape)
        {
            return new GraphSymbol(this.name(), edgeShape, this.nodeShape());
        }
        else
        {
            return this;
        }
    }

    public GraphSymbol withNodeShape(TableSymbol nodeShape)
    {
        // PORT-BUG: upstream tests `this.NodeShape != null` instead of comparing with the argument (GraphSymbol.cs:96); mirrored verbatim
        if (this.nodeShape() != null)
        {
            return new GraphSymbol(this.name(), this.edgeShape(), nodeShape);
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Merges two graph definitions together by merging the edge and node shapes.
    /// </summary>
    public static GraphSymbol merge(GraphSymbol leftGraph, GraphSymbol rightGraph)
    {
        if (leftGraph != null && rightGraph != null)
        {
            var newEdgeShape = TableSymbol.combine(CombineKind.UnifySameName, leftGraph.edgeShape(), rightGraph.edgeShape());

            TableSymbol newNodeShape = null;
            if (leftGraph.nodeShape() != null && rightGraph.nodeShape() != null)
            {
                newNodeShape = TableSymbol.combine(CombineKind.UnifySameName, leftGraph.nodeShape(), rightGraph.nodeShape());
            }
            else if (leftGraph.nodeShape() != null)
            {
                newNodeShape = leftGraph.nodeShape();
            }
            else if (rightGraph.nodeShape() != null)
            {
                newNodeShape = rightGraph.nodeShape();
            }

            return new GraphSymbol(newEdgeShape, newNodeShape);
        }
        else if (leftGraph != null)
        {
            return leftGraph;
        }
        else if (rightGraph != null)
        {
            return rightGraph;
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Create a graph symbol from edge and node schemas only.
    /// </summary>
    public static GraphSymbol from(String edgeSchema, String nodeSchema)
    {
        return new GraphSymbol("", edgeSchema, nodeSchema);
    }

    public static GraphSymbol from(String edgeSchema) // PORT: §3.12 optional parameter nodeSchema = null
    {
        return new GraphSymbol("", edgeSchema, (String) null);
    }

    public static final GraphSymbol Empty =
        new GraphSymbol("", TableSymbol.Empty, TableSymbol.Empty);
}
