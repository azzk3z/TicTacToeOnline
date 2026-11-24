const marks = ['x', '', 'o', '', 'x', '', 'o', '', 'x']

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-icon" aria-hidden="true"><span className="mark-x" /><span className="mark-o" /></span>
      Tic Tac Toe
    </div>
  )
}

export function BoardArt() {
  return (
    <aside className="board-art" aria-hidden="true">
      <div className="board-grid">
        {marks.map((mark, index) => (
          <div className="board-tile" key={index}>
            {mark && <span className={'board-mark mark-' + mark} />}
          </div>
        ))}
        <span className="winning-line" />
      </div>
    </aside>
  )
}
