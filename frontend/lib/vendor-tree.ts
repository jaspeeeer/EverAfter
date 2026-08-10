import type { VendorResponse } from "@/lib/types";

/**
 * Splits a flat vendor list into top-level vendors (packages and standalone vendors) and a
 * parentId -> items lookup. A "package" is simply a top-level vendor that has items nested
 * under it; there's no separate flag for it.
 */
export function groupVendorsByParent(vendors: VendorResponse[]): {
  topLevel: VendorResponse[];
  itemsByParent: Map<string, VendorResponse[]>;
} {
  const itemsByParent = new Map<string, VendorResponse[]>();
  const topLevel: VendorResponse[] = [];
  for (const v of vendors) {
    if (v.parentId) {
      const list = itemsByParent.get(v.parentId) ?? [];
      list.push(v);
      itemsByParent.set(v.parentId, list);
    } else {
      topLevel.push(v);
    }
  }
  return { topLevel, itemsByParent };
}

/**
 * Flattens vendors into tree order (a package immediately followed by its items) for simple
 * pickers like a `<select>` or checkbox list — each entry carries whether it should render
 * indented under its package.
 */
export function orderVendorsForPicker(
  vendors: VendorResponse[],
): Array<{ vendor: VendorResponse; indent: boolean }> {
  const { topLevel, itemsByParent } = groupVendorsByParent(vendors);
  const ordered: Array<{ vendor: VendorResponse; indent: boolean }> = [];
  for (const parent of topLevel) {
    ordered.push({ vendor: parent, indent: false });
    for (const item of itemsByParent.get(parent.id) ?? []) {
      ordered.push({ vendor: item, indent: true });
    }
  }
  return ordered;
}
