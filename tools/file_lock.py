"""Nonblocking process locks for local Windows and POSIX workers."""
import errno
import os


def acquire(handle):
    """Lock an open binary file until it closes; report contention consistently."""
    try:
        if os.name == "nt":
            import msvcrt
            # Windows permits locking a byte beyond EOF. Do not append on every
            # acquisition: the lock file must remain bounded across scheduler runs.
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError as error:
        if error.errno in (errno.EACCES, errno.EAGAIN, errno.EDEADLK):
            raise BlockingIOError("Another worker holds this lock") from error
        raise
