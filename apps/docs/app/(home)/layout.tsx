// The landing page draws its own nav (glass bar on desktop, the app's dock on phones), so no HomeLayout here.
export default function Layout({ children }: LayoutProps<"/">) {
  return children;
}
